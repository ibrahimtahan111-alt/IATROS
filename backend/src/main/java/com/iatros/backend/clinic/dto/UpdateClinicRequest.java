package com.iatros.backend.clinic.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateClinicRequest(
		@NotBlank @Size(max = 200) String name,
		@Email @Size(max = 254) String email,
		@Size(max = 40) String phone,
		@Size(max = 500) String address,
		@NotNull Long version) {
}
