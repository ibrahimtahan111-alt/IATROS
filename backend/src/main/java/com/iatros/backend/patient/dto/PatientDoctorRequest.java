package com.iatros.backend.patient.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Body of the primary-doctor change and of adding an authorized doctor. */
public record PatientDoctorRequest(@NotNull UUID doctorId) {
}
