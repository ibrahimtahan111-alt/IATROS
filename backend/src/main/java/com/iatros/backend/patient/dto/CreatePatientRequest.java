package com.iatros.backend.patient.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.iatros.backend.patient.Gender;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePatientRequest(
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotNull LocalDate dateOfBirth,
		Gender gender,
		@Size(max = 40) String phone,
		@Email @Size(max = 254) String email,
		@Size(max = 500) String address,
		@Size(max = 200) String emergencyContactName,
		@Size(max = 40) String emergencyContactPhone,
		@NotNull UUID primaryDoctorId) implements PatientDetailsRequest {
}
