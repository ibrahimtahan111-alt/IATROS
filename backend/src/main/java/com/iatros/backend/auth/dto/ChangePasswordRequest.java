package com.iatros.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank @Size(max = PasswordRules.MAX_LENGTH) String currentPassword,
		@NotBlank @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH) String newPassword,
		@NotBlank String confirmPassword) {

	@Override
	public String toString() {
		return "ChangePasswordRequest[***]";
	}

}
