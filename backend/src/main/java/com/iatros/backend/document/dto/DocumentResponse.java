package com.iatros.backend.document.dto;

import java.time.Instant;
import java.util.UUID;

import com.iatros.backend.medicalrecord.dto.AuthorRef;

/** Document metadata (api.md §21). {@code archivable}: whether the caller may archive it. */
public record DocumentResponse(
		UUID id,
		UUID patientId,
		UUID consultationId,
		String fileName,
		String contentType,
		long sizeBytes,
		AuthorRef uploadedBy,
		Instant uploadedAt,
		boolean archivable) {
}
