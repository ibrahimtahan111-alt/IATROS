package com.iatros.backend.appointment.dto;

import java.time.Instant;
import java.util.UUID;

import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.AppointmentType;

/** An appointment list row (api.md §17). */
public record AppointmentSummaryResponse(UUID id, Instant startAt, Instant endAt, NameRef patient, NameRef doctor,
		AppointmentType type, AppointmentStatus status, String reason) {
}
