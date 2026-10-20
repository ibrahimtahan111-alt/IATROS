package com.iatros.backend.shared.web;

import java.util.Locale;

import com.iatros.backend.shared.error.ValidationException;

/**
 * Turns a free-text {@code search} parameter into a lower-case "contains" LIKE pattern (api.md §28). LIKE wildcards
 * in the input are escaped with {@code \}, so queries must declare {@code escape '\'}.
 */
public final class SearchTerms {

	public static final int MAX_LENGTH = 100;

	private SearchTerms() {
	}

	/** Returns null when the term is absent or blank, meaning "no search filter". */
	public static String containsPattern(String term) {
		if (term == null || term.isBlank()) {
			return null;
		}
		String trimmed = term.trim();
		if (trimmed.length() > MAX_LENGTH) {
			throw ValidationException.of("search", "INVALID_LENGTH");
		}
		String escaped = trimmed.toLowerCase(Locale.ROOT)
			.replace("\\", "\\\\")
			.replace("%", "\\%")
			.replace("_", "\\_");
		return "%" + escaped + "%";
	}

}
