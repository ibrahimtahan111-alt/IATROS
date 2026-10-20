package com.iatros.backend.appointment.dto;

import java.util.UUID;

/** A patient or doctor shown on an appointment: booking-level data only (decisions.md D-029). */
public record NameRef(UUID id, String firstName, String lastName) {
}
