package com.iatros.backend.shared.error;

/** 401. Credentials or a session were rejected. Never states which part was wrong. */
public class AuthenticationFailedException extends ApiException {

	public AuthenticationFailedException(String code, String message) {
		super(code, message);
	}

}
