package com.iatros.backend.medicalrecord.dto;

import java.time.Instant;
import java.util.UUID;

import com.iatros.backend.medicalrecord.AllergySeverity;

public record AllergyResponse(UUID id, String allergen, String reaction, AllergySeverity severity, String notes,
		Instant updatedAt, long version) {
}
