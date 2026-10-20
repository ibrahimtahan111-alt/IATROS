package com.iatros.backend.consultation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A consultation in the patient's history list; the full notes are only in {@link ConsultationResponse}. */
public record ConsultationSummaryResponse(
		UUID id,
		Instant consultedAt,
		ConsultationDoctorRef doctor,
		String reason,
		String diagnosis,
		LocalDate followUpOn) {
}
