package com.iatros.backend.appointment.dto;

import java.time.Instant;
import java.util.UUID;

import com.iatros.backend.appointment.AppointmentType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAppointmentRequest(
		@NotNull UUID patientId,
		@NotNull UUID doctorId,
		@NotNull Instant startAt,
		@NotNull Instant endAt,
		@NotNull AppointmentType type,
		@Size(max = 500) String reason,
		@Size(max = 4000) String notes) {
}
