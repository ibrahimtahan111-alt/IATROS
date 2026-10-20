package com.iatros.backend.staff.dto;

import java.time.Instant;
import java.util.UUID;

import com.iatros.backend.user.Role;
import com.iatros.backend.user.UserStatus;

/** A clinic member as seen by the owner (api.md §7). {@code doctor} is set when the member has a doctor profile. */
public record StaffMemberResponse(UUID id, String firstName, String lastName, String email, String phone, Role role,
		UserStatus status, Instant lastLoginAt, Instant createdAt, long version, DoctorProfile doctor) {

	public record DoctorProfile(UUID id, String primarySpecialty, String additionalSpecialty,
			String registrationNumber) {
	}

}
