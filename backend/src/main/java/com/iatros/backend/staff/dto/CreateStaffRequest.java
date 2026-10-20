package com.iatros.backend.staff.dto;

import com.iatros.backend.user.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code doctor} is required when {@code role} is DOCTOR and must be absent otherwise (decisions.md D-022). */
public record CreateStaffRequest(
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank @Email @Size(max = 254) String email,
		@Size(max = 40) String phone,
		@NotNull Role role,
		@Valid DoctorProfileRequest doctor) {
}
