package com.iatros.backend.doctor.dto;

import java.util.UUID;

import com.iatros.backend.user.Role;
import com.iatros.backend.user.UserStatus;

/**
 * A doctor profile with the account fields needed to show it (api.md §8). {@code role} is DOCTOR, or OWNER for a
 * practising owner (D-033). {@code status} is the account status. {@code version} is the profile's version.
 */
public record DoctorResponse(UUID id, UUID userId, String firstName, String lastName, String email, String phone,
		Role role, UserStatus status, String primarySpecialty, String additionalSpecialty, String registrationNumber,
		long version) {
}
