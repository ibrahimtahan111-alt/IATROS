package com.iatros.backend.shared.error;

/** One invalid input field. {@code field} is null for errors that concern the whole request. */
public record FieldErrorResponse(String field, String code) {
}
