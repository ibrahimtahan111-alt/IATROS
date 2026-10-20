package com.iatros.backend.document;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The accepted document formats (decisions.md D-032). The type is recognised from the file's first bytes; the
 * client's declared content type is ignored and the file name's extension must agree with the content.
 */
public enum DocumentType {

	PDF("application/pdf", List.of("pdf")),
	JPEG("image/jpeg", List.of("jpg", "jpeg")),
	PNG("image/png", List.of("png")),
	WEBP("image/webp", List.of("webp"));

	/** Bytes needed to recognise every type. */
	static final int SIGNATURE_LENGTH = 12;

	private static final byte[] PDF_MAGIC = { '%', 'P', 'D', 'F', '-' };
	private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final byte[] PNG_MAGIC = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
	private static final byte[] RIFF = { 'R', 'I', 'F', 'F' };
	private static final byte[] WEBP_MARKER = { 'W', 'E', 'B', 'P' };

	private final String contentType;
	private final List<String> extensions;

	DocumentType(String contentType, List<String> extensions) {
		this.contentType = contentType;
		this.extensions = extensions;
	}

	public String contentType() {
		return contentType;
	}

	boolean acceptsExtension(String extension) {
		return extensions.contains(extension);
	}

	static boolean isAcceptedExtension(String extension) {
		return Arrays.stream(values()).anyMatch(type -> type.acceptsExtension(extension));
	}

	/** Recognises the type from the start of the file. */
	static Optional<DocumentType> detect(byte[] head) {
		if (startsWith(head, 0, PDF_MAGIC)) {
			return Optional.of(PDF);
		}
		if (startsWith(head, 0, JPEG_MAGIC)) {
			return Optional.of(JPEG);
		}
		if (startsWith(head, 0, PNG_MAGIC)) {
			return Optional.of(PNG);
		}
		if (startsWith(head, 0, RIFF) && startsWith(head, 8, WEBP_MARKER)) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] head, int offset, byte[] magic) {
		if (head.length < offset + magic.length) {
			return false;
		}
		for (int i = 0; i < magic.length; i++) {
			if (head[offset + i] != magic[i]) {
				return false;
			}
		}
		return true;
	}

}
