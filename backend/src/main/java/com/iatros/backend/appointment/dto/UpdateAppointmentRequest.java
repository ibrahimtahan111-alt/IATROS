package com.iatros.backend.appointment.dto;

import com.iatros.backend.appointment.AppointmentType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The appointment's details; time and doctor change through rescheduling. */
public record UpdateAppointmentRequest(
		@NotNull AppointmentType type,
		@Size(max = 500) String reason,
		@Size(max = 4000) String notes,
		@NotNull Long version) {
}
