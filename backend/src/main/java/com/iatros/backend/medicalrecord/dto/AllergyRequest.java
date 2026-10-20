package com.iatros.backend.medicalrecord.dto;

import com.iatros.backend.medicalrecord.AllergySeverity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code version} is required when updating and ignored when creating. */
public record AllergyRequest(
		@NotBlank @Size(max = 200) String allergen,
		@Size(max = 500) String reaction,
		AllergySeverity severity,
		@Size(max = 4000) String notes,
		Long version) {
}
