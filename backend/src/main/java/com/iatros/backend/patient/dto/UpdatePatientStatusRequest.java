package com.iatros.backend.patient.dto;

import com.iatros.backend.patient.PatientStatus;

import jakarta.validation.constraints.NotNull;

public record UpdatePatientStatusRequest(@NotNull PatientStatus status) {
}
