package com.iatros.backend.shared.error;

/** 403. The caller can see the resource but their role may not perform this operation. */
public class ForbiddenOperationException extends ApiException {

	public ForbiddenOperationException(String code, String message) {
		super(code, message);
	}

}
