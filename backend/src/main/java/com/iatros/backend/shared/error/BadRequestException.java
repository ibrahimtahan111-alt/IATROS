package com.iatros.backend.shared.error;

/** 400. The request parameters are invalid in a way bean validation cannot express (e.g. unknown sort field). */
public class BadRequestException extends ApiException {

	public BadRequestException(String code, String message) {
		super(code, message);
	}

}
