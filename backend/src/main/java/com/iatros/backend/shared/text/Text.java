package com.iatros.backend.shared.text;

/** Normalization of user-entered text before it is stored. */
public final class Text {

	private Text() {
	}

	/** Trims surrounding whitespace; blank values become null (optional fields are stored as NULL, not ""). */
	public static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/** Trims surrounding whitespace of a required value (presence is validated on the request). */
	public static String trim(String value) {
		return value == null ? null : value.strip();
	}

}
