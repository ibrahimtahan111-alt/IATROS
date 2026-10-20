package com.iatros.backend.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(@Size(max = 500) String reason, @NotNull Long version) {
}
