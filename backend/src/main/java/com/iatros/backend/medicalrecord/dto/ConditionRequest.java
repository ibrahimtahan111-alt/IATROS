package com.iatros.backend.medicalrecord.dto;

import java.time.LocalDate;

import com.iatros.backend.medicalrecord.ConditionStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code version} is required when updating and ignored when creating. */
public record ConditionRequest(
		@NotBlank @Size(max = 200) String name,
		@Size(max = 4000) String description,
		@NotNull ConditionStatus status,
		LocalDate diagnosedOn,
		Long version) {
}
