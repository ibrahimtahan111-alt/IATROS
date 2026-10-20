package com.iatros.backend.medicalrecord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code version} is required when updating and ignored when creating. */
public record NoteRequest(@NotBlank @Size(max = 20000) String content, Long version) {
}
