package com.iatros.backend.appointment.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Bookable start times of a doctor on one clinic-local date for a given duration (api.md §18). */
public record AvailabilityResponse(String timeZone, LocalDate date, int durationMinutes, List<Slot> slots) {

	public record Slot(Instant startAt, Instant endAt) {
	}

}
