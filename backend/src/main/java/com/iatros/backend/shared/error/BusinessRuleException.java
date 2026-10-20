package com.iatros.backend.shared.error;

/** 422. The request is well-formed but violates a business rule (e.g. invalid status transition). */
public class BusinessRuleException extends ApiException {

	public BusinessRuleException(String code, String message) {
		super(code, message);
	}

}
