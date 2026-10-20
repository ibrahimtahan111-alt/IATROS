package com.iatros.backend.shared.i18n;

/** Supported interface languages. Stored as lowercase ISO 639-1 codes. */
public enum Language {

	EN("en"),
	FR("fr");

	private final String code;

	Language(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}

	public static Language fromCode(String code) {
		for (Language language : values()) {
			if (language.code.equals(code)) {
				return language;
			}
		}
		throw new IllegalArgumentException("Unsupported language: " + code);
	}

}
