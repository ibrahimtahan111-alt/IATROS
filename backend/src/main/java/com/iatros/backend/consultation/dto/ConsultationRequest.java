package com.iatros.backend.consultation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create or update a consultation. The doctor is always the caller; {@code version} is required on update.
 * {@code appointmentId} links a new consultation to one of the doctor's appointments with the patient and is
 * ignored on update (the link never changes).
 */
public record ConsultationRequest(
		@NotNull Instant consultedAt,
		@NotBlank @Size(max = 500) String reason,
		@Size(max = 4000) String diagnosis,
		@Size(max = 20000) String clinicalNotes,
		@Size(max = 20000) String treatmentNotes,
		@Size(max = 4000) String followUpNotes,
		LocalDate followUpOn,
		UUID appointmentId,
		Long version) {
}
