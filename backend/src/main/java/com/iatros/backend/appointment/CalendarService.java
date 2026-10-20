package com.iatros.backend.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.access.AccessPolicy;
import com.iatros.backend.appointment.dto.AvailabilityResponse;
import com.iatros.backend.appointment.dto.CalendarResponse;
import com.iatros.backend.appointment.dto.NameRef;
import com.iatros.backend.clinic.ClinicTime;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.schedule.TimeRange;
import com.iatros.backend.schedule.WorkingHoursService;
import com.iatros.backend.schedule.dto.WorkingHoursResponse;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;
import com.iatros.backend.user.UserStatus;

/**
 * Calendar reads (api.md §18–19): the events and working hours of a date range, and the bookable start times of
 * one doctor on one date. Availability always comes from {@link WorkingHoursService} (D-035).
 */
@Service
@Transactional(readOnly = true)
public class CalendarService {

	static final int MAX_RANGE_DAYS = 42;
	static final int DEFAULT_DURATION_MINUTES = 30;
	static final int SLOT_STEP_MINUTES = 15;

	private final AppointmentRepository appointments;
	private final AppointmentDirectory directory;
	private final DoctorRepository doctors;
	private final WorkingHoursService workingHours;
	private final AccessPolicy access;
	private final ClinicTime clinicTime;
	private final Clock clock;

	CalendarService(AppointmentRepository appointments, AppointmentDirectory directory, DoctorRepository doctors,
			WorkingHoursService workingHours, AccessPolicy access, ClinicTime clinicTime, Clock clock) {
		this.appointments = appointments;
		this.directory = directory;
		this.doctors = doctors;
		this.workingHours = workingHours;
		this.access = access;
		this.clinicTime = clinicTime;
		this.clock = clock;
	}

	/**
	 * Owners and secretaries see every doctor of the clinic (or the requested ones); doctors only themselves.
	 * Without a doctor filter, deactivated doctors are shown only if they still have events in the range.
	 */
	public CalendarResponse events(AuthenticatedUser caller, LocalDate from, LocalDate to, List<UUID> doctorIds,
			Collection<AppointmentStatus> statuses) {
		requireRange(from, to);
		List<Doctor> requested = requestedDoctors(caller, doctorIds);
		ZoneId zone = clinicTime.zone(caller.clinicId());
		List<Appointment> found = requested.isEmpty() ? List.of()
				: appointments.findOverlapping(caller.clinicId(), requested.stream().map(Doctor::getId).toList(),
						from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
		List<Appointment> events = statuses == null || statuses.isEmpty() ? found
				: found.stream().filter(appointment -> statuses.contains(appointment.getStatus())).toList();

		Map<UUID, AppUser> usersById = directory.usersById(requested.stream().map(Doctor::getUserId).toList());
		Set<UUID> doctorsWithEvents = found.stream().map(Appointment::getDoctorId).collect(Collectors.toSet());
		boolean explicit = doctorIds != null && !doctorIds.isEmpty();
		List<Doctor> shown = requested.stream()
			.filter(doctor -> explicit || doctorsWithEvents.contains(doctor.getId())
					|| usersById.get(doctor.getUserId()).getStatus() != UserStatus.DEACTIVATED)
			.sorted(Comparator.comparing((Doctor doctor) -> usersById.get(doctor.getUserId()).getLastName(),
					String.CASE_INSENSITIVE_ORDER)
				.thenComparing(doctor -> usersById.get(doctor.getUserId()).getFirstName(),
						String.CASE_INSENSITIVE_ORDER))
			.toList();

		Map<UUID, NameRef> patientRefs = directory
			.patients(events.stream().map(Appointment::getPatientId).collect(Collectors.toSet()));
		return new CalendarResponse(zone.getId(), from, to,
				events.stream()
					.map(appointment -> new CalendarResponse.Event(appointment.getId(), appointment.getStartAt(),
							appointment.getEndAt(), appointment.getDoctorId(),
							patientRefs.get(appointment.getPatientId()), appointment.getType(),
							appointment.getStatus()))
					.toList(),
				shown.stream().map(doctor -> {
					AppUser user = usersById.get(doctor.getUserId());
					return new CalendarResponse.DoctorHours(doctor.getId(), user.getFirstName(), user.getLastName(),
							days(doctor, from, to));
				}).toList());
	}

	/**
	 * Start times, every {@value #SLOT_STEP_MINUTES} minutes within each working range, at which an appointment of
	 * {@code durationMinutes} fits without overlapping a time-blocking appointment and does not start in the past.
	 */
	public AvailabilityResponse availability(AuthenticatedUser caller, UUID doctorId, LocalDate date,
			Integer durationMinutes) {
		Doctor doctor = access.requireDoctorCalendar(caller, doctorId);
		int duration = durationMinutes == null ? DEFAULT_DURATION_MINUTES : durationMinutes;
		if (duration < 5 || duration > Appointment.MAX_DURATION.toMinutes()) {
			throw ValidationException.of("duration", "OUT_OF_RANGE");
		}
		ZoneId zone = clinicTime.zone(caller.clinicId());
		List<Appointment> blocking = appointments
			.findOverlapping(caller.clinicId(), List.of(doctor.getId()), date.atStartOfDay(zone).toInstant(),
					date.plusDays(1).atStartOfDay(zone).toInstant())
			.stream()
			.filter(appointment -> appointment.getStatus().blocksTime())
			.toList();
		Instant earliest = Instant.now(clock).minus(AppointmentService.CLOCK_SKEW);

		List<AvailabilityResponse.Slot> slots = new ArrayList<>();
		for (TimeRange range : workingHours.workingHours(doctor, date, date).get(date)) {
			int last = minuteOfDay(range.end()) - duration;
			for (int minute = minuteOfDay(range.start()); minute <= last; minute += SLOT_STEP_MINUTES) {
				Instant start = date.atTime(LocalTime.MIN.plusMinutes(minute)).atZone(zone).toInstant();
				Instant end = start.plus(duration, ChronoUnit.MINUTES);
				boolean free = blocking.stream()
					.noneMatch(appointment -> appointment.getStartAt().isBefore(end)
							&& appointment.getEndAt().isAfter(start));
				if (!start.isBefore(earliest) && free) {
					slots.add(new AvailabilityResponse.Slot(start, end));
				}
			}
		}
		return new AvailabilityResponse(zone.getId(), date, duration, slots);
	}

	private List<Doctor> requestedDoctors(AuthenticatedUser caller, List<UUID> doctorIds) {
		if (doctorIds != null && !doctorIds.isEmpty()) {
			return doctorIds.stream().distinct().map(id -> access.requireDoctorCalendar(caller, id)).toList();
		}
		if (caller.hasRole(Role.DOCTOR)) {
			UUID own = access.dataScope(caller).doctorId().orElseThrow();
			return List.of(access.requireDoctorCalendar(caller, own));
		}
		return doctors.findAllByClinicId(caller.clinicId());
	}

	private List<WorkingHoursResponse.Day> days(Doctor doctor, LocalDate from, LocalDate to) {
		return workingHours.workingHours(doctor, from, to)
			.entrySet()
			.stream()
			.map(day -> new WorkingHoursResponse.Day(day.getKey(),
					day.getValue()
						.stream()
						.map(range -> new WorkingHoursResponse.Range(range.start(), range.end()))
						.toList()))
			.toList();
	}

	private static void requireRange(LocalDate from, LocalDate to) {
		if (to.isBefore(from)) {
			throw ValidationException.of("to", "END_BEFORE_START");
		}
		if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
			throw ValidationException.of("to", "OUT_OF_RANGE");
		}
	}

	private static int minuteOfDay(LocalTime time) {
		return time.getHour() * 60 + time.getMinute();
	}

}
