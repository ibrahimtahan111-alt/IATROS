package com.iatros.backend.medicalrecord.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code editable}: the caller wrote the note, so they may edit or remove it (D-037). */
public record NoteResponse(UUID id, String content, AuthorRef author, Instant createdAt, Instant updatedAt,
		boolean edited, boolean editable, long version) {
}
