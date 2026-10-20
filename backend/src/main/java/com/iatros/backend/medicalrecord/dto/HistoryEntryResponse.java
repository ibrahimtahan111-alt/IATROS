package com.iatros.backend.medicalrecord.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HistoryEntryResponse(UUID id, String description, LocalDate occurredOn, String notes, Instant updatedAt,
		long version) {
}
