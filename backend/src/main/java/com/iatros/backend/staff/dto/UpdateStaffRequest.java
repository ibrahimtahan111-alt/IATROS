package com.iatros.backend.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Account details. The email can change only while the member is INVITED (decisions.md D-034). */
public record UpdateStaffRequest(
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank @Email @Size(max = 254) String email,
		@Size(max = 40) String phone,
		@NotNull Long version) {
}
