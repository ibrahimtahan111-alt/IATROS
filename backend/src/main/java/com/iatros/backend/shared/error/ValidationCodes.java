package com.iatros.backend.shared.error;

import java.util.Map;

/** Translates Bean Validation constraint names into the stable field-error codes used by the frontend. */
final class ValidationCodes {

	static final String INVALID = "INVALID";

	private static final Map<String, String> BY_CONSTRAINT = Map.ofEntries(
			Map.entry("NotNull", "REQUIRED"),
			Map.entry("NotBlank", "REQUIRED"),
			Map.entry("NotEmpty", "REQUIRED"),
			Map.entry("Size", "INVALID_LENGTH"),
			Map.entry("Length", "INVALID_LENGTH"),
			Map.entry("Email", "INVALID_EMAIL"),
			Map.entry("Pattern", "INVALID_FORMAT"),
			Map.entry("typeMismatch", "INVALID_FORMAT"),
			Map.entry("Min", "OUT_OF_RANGE"),
			Map.entry("Max", "OUT_OF_RANGE"),
			Map.entry("DecimalMin", "OUT_OF_RANGE"),
			Map.entry("DecimalMax", "OUT_OF_RANGE"),
			Map.entry("Positive", "OUT_OF_RANGE"),
			Map.entry("PositiveOrZero", "OUT_OF_RANGE"),
			Map.entry("Range", "OUT_OF_RANGE"),
			Map.entry("Past", "INVALID_DATE"),
			Map.entry("PastOrPresent", "INVALID_DATE"),
			Map.entry("Future", "INVALID_DATE"),
			Map.entry("FutureOrPresent", "INVALID_DATE"));

	private ValidationCodes() {
	}

	static String forConstraint(String constraintName) {
		return constraintName == null ? INVALID : BY_CONSTRAINT.getOrDefault(constraintName, INVALID);
	}

}
