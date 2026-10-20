package com.iatros.backend.schedule;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.access.AccessPolicy;
import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.schedule.dto.CreateScheduleExceptionRequest;
import com.iatros.backend.schedule.dto.ScheduleExceptionResponse;
import com.iatros.backend.schedule.dto.SchedulePeriodDto;
import com.iatros.backend.schedule.dto.UpdateScheduleRequest;
import com.iatros.backend.schedule.dto.WeeklyScheduleResponse;
import com.iatros.backend.schedule.dto.WorkingHoursResponse;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.FieldErrorResponse;
import com.iatros.backend.shared.error.NotFoundException;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.text.Text;

/**
 * Weekly schedules, schedule exceptions, and effective working hours (api.md §20, decisions.md D-025, D-026, D-035).
 * Reading: owners and secretaries for any clinic doctor, doctors for themselves. Changing: owners for any clinic
 * doctor, doctors for themselves, never secretaries (AccessPolicy).
 */
@Service
public class ScheduleService {

	static final int MAX_EXCEPTION_RANGE_DAYS = 366;
	static final int MAX_WORKING_HOURS_RANGE_DAYS = 42;

	private final AccessPolicy access;
	private final DoctorRepository doctors;
	private final DoctorSchedulePeriodRepository periods;
	private final ScheduleExceptionRepository exceptions;
	private final WorkingHoursService workingHours;
	private final AuditService audit;
	private final Clock clock;

	public ScheduleService(AccessPolicy access, DoctorRepository doctors, DoctorSchedulePeriodRepository periods,
			ScheduleExceptionRepository exceptions, WorkingHoursService workingHours, AuditService audit,
			Clock clock) {
		this.access = access;
		this.doctors = doctors;
		this.periods = periods;
		this.exceptions = exceptions;
		this.workingHours = workingHours;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public WeeklyScheduleResponse getWeekly(AuthenticatedUser caller, UUID doctorId) {
		Doctor doctor = access.requireDoctorCalendar(caller, doctorId);
		return weekly(doctor, doctor.getScheduleVersion());
	}

	/**
	 * Replaces the whole week atomically. Periods may touch (12:00–14:00 then 14:00–18:00) but not overlap; the gaps
	 * between periods are the doctor's breaks. Existing appointments are not changed.
	 */
	@Transactional
	public WeeklyScheduleResponse replaceWeekly(AuthenticatedUser caller, UUID doctorId,
			UpdateScheduleRequest request) {
		Doctor doctor = access.requireDoctorManagement(caller, doctorId);
		List<SchedulePeriodDto> requested = request.periods().stream()
			.map(period -> new SchedulePeriodDto(period.dayOfWeek(), minutes(period.startTime()),
					minutes(period.endTime())))
			.toList();
		validateWeek(requested);

		if (doctors.advanceScheduleVersion(doctor.getId(), request.version()) == 0) {
			throw new ObjectOptimisticLockingFailureException(Doctor.class, doctor.getId());
		}
		periods.deleteAllForDoctor(doctor.getId());
		periods.saveAllAndFlush(requested.stream()
			.map(period -> new DoctorSchedulePeriod(doctor.getId(), DayOfWeek.of(period.dayOfWeek()),
					period.startTime(), period.endTime()))
			.toList());
		audit.record(caller.clinicId(), caller.userId(), AuditActions.SCHEDULE_UPDATED, AuditActions.ENTITY_DOCTOR,
				doctor.getId(), Map.of("periods", requested.size()));
		return weekly(doctor, request.version() + 1);
	}

	/** Exceptions in {@code [from, to]}; by default from today (clinic time) for one year. */
	@Transactional(readOnly = true)
	public List<ScheduleExceptionResponse> listExceptions(AuthenticatedUser caller, UUID doctorId, LocalDate from,
			LocalDate to) {
		Doctor doctor = access.requireDoctorCalendar(caller, doctorId);
		LocalDate start = from != null ? from : today(doctor);
		LocalDate end = to != null ? to : start.plusDays(MAX_EXCEPTION_RANGE_DAYS - 1);
		requireRange(start, end, MAX_EXCEPTION_RANGE_DAYS);
		return exceptions
			.findAllByDoctorIdAndExceptionDateBetweenOrderByExceptionDateAscStartTimeAsc(doctor.getId(), start, end)
			.stream()
			.map(ScheduleService::toResponse)
			.toList();
	}

	@Transactional
	public ScheduleExceptionResponse createException(AuthenticatedUser caller, UUID doctorId,
			CreateScheduleExceptionRequest request) {
		Doctor doctor = access.requireDoctorManagement(caller, doctorId);
		LocalTime start = request.startTime() == null ? null : minutes(request.startTime());
		LocalTime end = request.endTime() == null ? null : minutes(request.endTime());
		validateException(doctor, request.date(), request.type(), start, end);

		String reason = Text.trimToNull(request.reason());
		ScheduleException exception = switch (request.type()) {
			case UNAVAILABLE -> start == null
					? ScheduleException.unavailableAllDay(doctor.getId(), request.date(), reason, caller.userId())
					: ScheduleException.unavailable(doctor.getId(), request.date(), start, end, reason,
							caller.userId());
			case EXTRA_AVAILABILITY -> ScheduleException.extraAvailability(doctor.getId(), request.date(), start, end,
					reason, caller.userId());
		};
		exceptions.saveAndFlush(exception);
		audit.record(caller.clinicId(), caller.userId(), AuditActions.SCHEDULE_EXCEPTION_CREATED,
				AuditActions.ENTITY_SCHEDULE_EXCEPTION, exception.getId(),
				Map.of("doctorId", doctor.getId().toString(), "type", exception.getType().name()));
		return toResponse(exception);
	}

	@Transactional
	public void deleteException(AuthenticatedUser caller, UUID doctorId, UUID exceptionId) {
		Doctor doctor = access.requireDoctorManagement(caller, doctorId);
		ScheduleException exception = exceptions.findByIdAndDoctorId(exceptionId, doctor.getId())
			.orElseThrow(() -> NotFoundException.of("SCHEDULE_EXCEPTION"));
		exceptions.delete(exception);
		audit.record(caller.clinicId(), caller.userId(), AuditActions.SCHEDULE_EXCEPTION_DELETED,
				AuditActions.ENTITY_SCHEDULE_EXCEPTION, exception.getId(),
				Map.of("doctorId", doctor.getId().toString()));
	}

	@Transactional(readOnly = true)
	public WorkingHoursResponse workingHours(AuthenticatedUser caller, UUID doctorId, LocalDate from, LocalDate to) {
		Doctor doctor = access.requireDoctorCalendar(caller, doctorId);
		requireRange(from, to, MAX_WORKING_HOURS_RANGE_DAYS);
		List<WorkingHoursResponse.Day> days = workingHours.workingHours(doctor, from, to).entrySet().stream()
			.map(day -> new WorkingHoursResponse.Day(day.getKey(), day.getValue().stream()
				.map(range -> new WorkingHoursResponse.Range(range.start(), range.end()))
				.toList()))
			.toList();
		return new WorkingHoursResponse(workingHours.clinicZone(doctor).getId(), days);
	}

	private WeeklyScheduleResponse weekly(Doctor doctor, long version) {
		List<SchedulePeriodDto> week = periods.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctor.getId())
			.stream()
			.map(period -> new SchedulePeriodDto(period.getDayOfWeek().getValue(), period.getStartTime(),
					period.getEndTime()))
			.toList();
		return new WeeklyScheduleResponse(doctor.getId(), workingHours.clinicZone(doctor).getId(), version, week);
	}

	/** Reports every invalid period at once, by index, so the editor can mark each one. */
	private static void validateWeek(List<SchedulePeriodDto> week) {
		List<FieldErrorResponse> errors = new ArrayList<>();
		for (int i = 0; i < week.size(); i++) {
			SchedulePeriodDto period = week.get(i);
			if (!period.endTime().isAfter(period.startTime())) {
				errors.add(new FieldErrorResponse("periods[" + i + "].endTime", "END_BEFORE_START"));
			}
		}
		if (errors.isEmpty()) {
			List<Integer> order = IntStream.range(0, week.size()).boxed()
				.sorted(Comparator.comparing((Integer i) -> week.get(i).dayOfWeek())
					.thenComparing(i -> week.get(i).startTime()))
				.toList();
			for (int k = 1; k < order.size(); k++) {
				SchedulePeriodDto previous = week.get(order.get(k - 1));
				SchedulePeriodDto current = week.get(order.get(k));
				if (previous.dayOfWeek().equals(current.dayOfWeek())
						&& current.startTime().isBefore(previous.endTime())) {
					errors.add(new FieldErrorResponse("periods[" + order.get(k) + "].startTime", "OVERLAP"));
				}
			}
		}
		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}
	}

	private void validateException(Doctor doctor, LocalDate date, ScheduleExceptionType type, LocalTime start,
			LocalTime end) {
		List<FieldErrorResponse> errors = new ArrayList<>();
		if (date.isBefore(today(doctor))) {
			errors.add(new FieldErrorResponse("date", "DATE_IN_PAST"));
		}
		boolean timesRequired = type == ScheduleExceptionType.EXTRA_AVAILABILITY || start != null || end != null;
		if (timesRequired && start == null) {
			errors.add(new FieldErrorResponse("startTime", "REQUIRED"));
		}
		if (timesRequired && end == null) {
			errors.add(new FieldErrorResponse("endTime", "REQUIRED"));
		}
		if (start != null && end != null && !end.isAfter(start)) {
			errors.add(new FieldErrorResponse("endTime", "END_BEFORE_START"));
		}
		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}
	}

	private static void requireRange(LocalDate from, LocalDate to, int maxDays) {
		if (from == null || to == null) {
			throw ValidationException.of(from == null ? "from" : "to", "REQUIRED");
		}
		if (to.isBefore(from)) {
			throw ValidationException.of("to", "END_BEFORE_START");
		}
		if (ChronoUnit.DAYS.between(from, to) >= maxDays) {
			throw ValidationException.of("to", "OUT_OF_RANGE");
		}
	}

	private LocalDate today(Doctor doctor) {
		ZoneId zone = workingHours.clinicZone(doctor);
		return LocalDate.now(clock.withZone(zone));
	}

	private static LocalTime minutes(LocalTime time) {
		return time.truncatedTo(ChronoUnit.MINUTES);
	}

	private static ScheduleExceptionResponse toResponse(ScheduleException exception) {
		return new ScheduleExceptionResponse(exception.getId(), exception.getExceptionDate(), exception.getType(),
				exception.getStartTime(), exception.getEndTime(), exception.getReason(), exception.getCreatedAt());
	}

}
