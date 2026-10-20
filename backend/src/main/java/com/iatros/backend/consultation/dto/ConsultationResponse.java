package com.iatros.backend.consultation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A full consultation. {@code editable} is true only for the consultation's own doctor (D-037). */
public record ConsultationResponse(
		UUID id,
		UUID patientId,
		Instant consultedAt,
		ConsultationDoctorRef doctor,
		String reason,
		String diagnosis,
		String clinicalNotes,
		String treatmentNotes,
		String followUpNotes,
		LocalDate followUpOn,
		UUID appointmentId,
		Instant createdAt,
		Instant updatedAt,
		boolean editable,
		long version) {
}
