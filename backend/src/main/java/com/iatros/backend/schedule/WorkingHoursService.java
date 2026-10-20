package com.iatros.backend.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.clinic.ClinicSettingsRepository;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.UserStatus;

/**
 * A doctor's effective working hours: the weekly schedule plus the exceptions of each date (decisions.md D-025,
 * D-026, D-035), in the clinic's time zone (D-004). This is the single source of availability; the frontend never
 * computes it.
 *
 * <p>For a date: weekly periods of that weekday and extra-availability exceptions are merged, then unavailable
 * exceptions are removed (an all-day one removes everything). Deactivated doctors have no working hours.
 *
 * <p>Callers must have resolved the doctor within their clinic (schedule rows carry no clinic_id, D-009).
 */
@Service
@Transactional(readOnly = true)
public class WorkingHoursService {

	private final DoctorSchedulePeriodRepository periods;
	private final ScheduleExceptionRepository exceptions;
	private final ClinicSettingsRepository clinicSettings;
	private final AppUserRepository users;

	public WorkingHoursService(DoctorSchedulePeriodRepository periods, ScheduleExceptionRepository exceptions,
			ClinicSettingsRepository clinicSettings, AppUserRepository users) {
		this.periods = periods;
		this.exceptions = exceptions;
		this.clinicSettings = clinicSettings;
		this.users = users;
	}

	/** Working hours for each date of {@code [from, to]}, in date order. Dates without hours map to an empty list. */
	public Map<LocalDate, List<TimeRange>> workingHours(Doctor doctor, LocalDate from, LocalDate to) {
		Map<LocalDate, List<TimeRange>> days = new LinkedHashMap<>();
		boolean active = users.findById(doctor.getUserId())
			.map(user -> user.getStatus() != UserStatus.DEACTIVATED)
			.orElse(false);
		if (!active) {
			from.datesUntil(to.plusDays(1)).forEach(date -> days.put(date, List.of()));
			return days;
		}

		Map<DayOfWeek, List<TimeRange>> weekly = periods.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctor.getId())
			.stream()
			.collect(Collectors.groupingBy(DoctorSchedulePeriod::getDayOfWeek,
					Collectors.mapping(period -> new TimeRange(period.getStartTime(), period.getEndTime()),
							Collectors.toList())));
		Map<LocalDate, List<ScheduleException>> exceptionsByDate = exceptions
			.findAllByDoctorIdAndExceptionDateBetweenOrderByExceptionDateAscStartTimeAsc(doctor.getId(), from, to)
			.stream()
			.collect(Collectors.groupingBy(ScheduleException::getExceptionDate));

		from.datesUntil(to.plusDays(1)).forEach(date -> days.put(date,
				hoursFor(weekly.getOrDefault(date.getDayOfWeek(), List.of()),
						exceptionsByDate.getOrDefault(date, List.of()))));
		return days;
	}

	/**
	 * Whether {@code [start, end)} lies entirely within the doctor's working hours on one local date. Used to
	 * validate bookings (D-025: there is no override; extra availability is added as an exception first).
	 */
	public boolean isAvailable(Doctor doctor, Instant start, Instant end) {
		if (!end.isAfter(start)) {
			return false;
		}
		ZoneId zone = clinicZone(doctor);
		ZonedDateTime localStart = start.atZone(zone);
		ZonedDateTime localEnd = end.atZone(zone);
		LocalDate date = localStart.toLocalDate();
		if (!localEnd.toLocalDate().equals(date)) {
			return false;
		}
		return workingHours(doctor, date, date).get(date)
			.stream()
			.anyMatch(range -> range.contains(localStart.toLocalTime(), localEnd.toLocalTime()));
	}

	public ZoneId clinicZone(Doctor doctor) {
		return clinicSettings.findById(doctor.getClinicId())
			.map(settings -> settings.getTimeZone())
			.orElseThrow(() -> new IllegalStateException("Clinic settings missing for " + doctor.getClinicId()));
	}

	static List<TimeRange> hoursFor(List<TimeRange> weekly, List<ScheduleException> dayExceptions) {
		List<TimeRange> open = new ArrayList<>(weekly);
		List<TimeRange> closed = new ArrayList<>();
		for (ScheduleException exception : dayExceptions) {
			if (exception.getType() == ScheduleExceptionType.UNAVAILABLE && exception.isAllDay()) {
				return List.of();
			}
			TimeRange range = new TimeRange(exception.getStartTime(), exception.getEndTime());
			if (exception.getType() == ScheduleExceptionType.EXTRA_AVAILABILITY) {
				open.add(range);
			}
			else {
				closed.add(range);
			}
		}
		return TimeRange.subtract(TimeRange.union(open), closed);
	}

}
