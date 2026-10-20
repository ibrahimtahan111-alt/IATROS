package com.iatros.backend.auth.dto;

import java.util.UUID;

import com.iatros.backend.user.Role;

/**
 * The authenticated user's context for the frontend (api.md §5, GET /auth/me).
 *
 * @param doctorId the user's doctor profile, if any (doctors, and owners who practice; decisions.md D-033)
 * @param preferredLanguage {@code en}/{@code fr}, or null to follow the clinic default
 */
public record CurrentUserResponse(
		UUID id,
		String firstName,
		String lastName,
		String email,
		Role role,
		String preferredLanguage,
		UUID doctorId,
		ClinicSummary clinic) {

	/** @param timeZone IANA zone used to interpret schedules; null until clinic settings exist */
	public record ClinicSummary(UUID id, String name, String timeZone, String defaultLanguage) {
	}

}
