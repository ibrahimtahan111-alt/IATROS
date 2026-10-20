package com.iatros.backend.shared.error;

import java.util.List;

/** 400 VALIDATION_ERROR raised by services for field rules that annotations cannot express (e.g. password confirmation). */
public class ValidationException extends ApiException {

	private final List<FieldErrorResponse> fieldErrors;

	public ValidationException(List<FieldErrorResponse> fieldErrors) {
		super(GlobalExceptionHandler.VALIDATION_ERROR, "Validation failed");
		this.fieldErrors = List.copyOf(fieldErrors);
	}

	public static ValidationException of(String field, String code) {
		return new ValidationException(List.of(new FieldErrorResponse(field, code)));
	}

	public List<FieldErrorResponse> getFieldErrors() {
		return fieldErrors;
	}

}
