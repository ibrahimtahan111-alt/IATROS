package com.iatros.backend.document;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Cleans client file names for display and download only; they never determine where a file is stored. Path parts
 * and control characters are removed, and long names are shortened while keeping their extension.
 */
final class DocumentFileNames {

	static final int MAX_LENGTH = 255;

	private DocumentFileNames() {
	}

	/** The cleaned name, or an empty string when nothing usable remains. */
	static String clean(String original) {
		if (original == null) {
			return "";
		}
		String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
		name = Normalizer.normalize(name, Normalizer.Form.NFC).replaceAll("\\p{Cntrl}", "").strip();
		if (name.length() <= MAX_LENGTH) {
			return name;
		}
		String extension = extensionWithDot(name);
		return name.substring(0, MAX_LENGTH - extension.length()).strip() + extension;
	}

	/** Lower-case extension without the dot, or an empty string. */
	static String extension(String name) {
		String withDot = extensionWithDot(name);
		return withDot.isEmpty() ? "" : withDot.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String extensionWithDot(String name) {
		int dot = name.lastIndexOf('.');
		return dot <= 0 || dot == name.length() - 1 || name.length() - dot > 10 ? "" : name.substring(dot);
	}

}
