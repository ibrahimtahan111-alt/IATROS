package com.iatros.backend.appointment.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.AppointmentType;
import com.iatros.backend.schedule.dto.WorkingHoursResponse;

/**
 * Everything one calendar screen needs (api.md §19): the events of the range and, for each shown doctor, the
 * effective working hours of every date, so unavailable time can be shaded without further requests.
 */
public record CalendarResponse(String timeZone, LocalDate from, LocalDate to, List<Event> events,
		List<DoctorHours> doctors) {

	public record Event(UUID id, Instant startAt, Instant endAt, UUID doctorId, NameRef patient,
			AppointmentType type, AppointmentStatus status) {
	}

	public record DoctorHours(UUID id, String firstName, String lastName, List<WorkingHoursResponse.Day> days) {
	}

}
