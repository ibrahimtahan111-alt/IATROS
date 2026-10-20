package com.iatros.backend.medicalrecord.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code version} is required when updating and ignored when creating. */
public record MedicationRequest(
		@NotBlank @Size(max = 200) String name,
		@Size(max = 100) String dosage,
		@Size(max = 100) String frequency,
		@Size(max = 4000) String notes,
		LocalDate startDate,
		LocalDate endDate,
		@NotNull Boolean active,
		Long version) {
}
