package com.iatros.backend.medicalrecord.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MedicationResponse(UUID id, String name, String dosage, String frequency, String notes,
		LocalDate startDate, LocalDate endDate, boolean active, Instant updatedAt, long version) {
}
