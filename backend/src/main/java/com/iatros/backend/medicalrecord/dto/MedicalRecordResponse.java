package com.iatros.backend.medicalrecord.dto;

import java.util.List;
import java.util.UUID;

/**
 * The structured part of a record. Each section is small by nature and returned whole; notes and consultations grow
 * over time and are paginated separately.
 */
public record MedicalRecordResponse(UUID patientId, List<AllergyResponse> allergies,
		List<ConditionResponse> conditions, List<MedicationResponse> medications,
		List<HistoryEntryResponse> history) {
}
