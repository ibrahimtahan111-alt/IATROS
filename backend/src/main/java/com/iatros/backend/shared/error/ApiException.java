package com.iatros.backend.shared.error;

/**
 * Base class for expected, client-facing failures. The {@code code} is a stable, machine-readable
 * identifier returned in the problem response; the message is for logs only and never sent to clients.
 */
public abstract class ApiException extends RuntimeException {

	private final String code;

	protected ApiException(String code, String message) {
		super(message, null, false, false);
		this.code = code;
	}

	public String getCode() {
		return code;
	}

}
