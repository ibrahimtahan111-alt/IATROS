package com.iatros.backend.shared.error;

/** 409. The request conflicts with the current state of a resource (e.g. double-booking, duplicate email). */
public class ConflictException extends ApiException {

	public ConflictException(String code, String message) {
		super(code, message);
	}

}
