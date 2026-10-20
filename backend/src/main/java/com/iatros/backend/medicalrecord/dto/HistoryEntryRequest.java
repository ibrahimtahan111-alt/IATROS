package com.iatros.backend.medicalrecord.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code version} is required when updating and ignored when creating. */
public record HistoryEntryRequest(
		@NotBlank @Size(max = 4000) String description,
		LocalDate occurredOn,
		@Size(max = 4000) String notes,
		Long version) {
}
