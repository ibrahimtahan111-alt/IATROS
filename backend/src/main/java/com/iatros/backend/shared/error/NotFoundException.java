package com.iatros.backend.shared.error;

/**
 * 404. Also used when a resource exists but belongs to another clinic or is outside the caller's
 * authorized scope, so its existence is not revealed (security.md).
 */
public class NotFoundException extends ApiException {

	public NotFoundException(String code, String message) {
		super(code, message);
	}

	public static NotFoundException of(String resource) {
		return new NotFoundException(resource + "_NOT_FOUND", resource + " not found");
	}

}
