package com.iatros.backend.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Professional details only; name and contact belong to the account (api.md §7). */
public record UpdateDoctorProfileRequest(
		@NotBlank @Size(max = 50) String primarySpecialty,
		@Size(max = 200) String additionalSpecialty,
		@Size(max = 50) String registrationNumber,
		@NotNull Long version) {
}
