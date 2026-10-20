package com.iatros.backend.medicalrecord.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.iatros.backend.medicalrecord.ConditionStatus;

public record ConditionResponse(UUID id, String name, String description, ConditionStatus status,
		LocalDate diagnosedOn, Instant updatedAt, long version) {
}
