package com.iatros.backend.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code primarySpecialty} is a specialty code from GET /api/v1/specialties. */
public record DoctorProfileRequest(
		@NotBlank @Size(max = 50) String primarySpecialty,
		@Size(max = 200) String additionalSpecialty,
		@Size(max = 50) String registrationNumber) {
}
