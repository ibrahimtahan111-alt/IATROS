package com.iatros.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sets a password using a single-use token (password reset or invitation acceptance). */
public record TokenPasswordRequest(
		@NotBlank @Size(max = 128) String token,
		@NotBlank @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH) String newPassword,
		@NotBlank String confirmPassword) {

	@Override
	public String toString() {
		return "TokenPasswordRequest[***]";
	}

}
