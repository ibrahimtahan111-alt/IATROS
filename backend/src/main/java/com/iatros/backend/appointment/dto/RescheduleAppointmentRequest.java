package com.iatros.backend.appointment.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record RescheduleAppointmentRequest(
		@NotNull UUID doctorId,
		@NotNull Instant startAt,
		@NotNull Instant endAt,
		@NotNull Long version) {
}
