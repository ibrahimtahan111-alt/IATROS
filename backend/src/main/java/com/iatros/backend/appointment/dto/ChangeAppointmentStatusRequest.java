package com.iatros.backend.appointment.dto;

import com.iatros.backend.appointment.AppointmentStatus;

import jakarta.validation.constraints.NotNull;

public record ChangeAppointmentStatusRequest(@NotNull AppointmentStatus status, @NotNull Long version) {
}
