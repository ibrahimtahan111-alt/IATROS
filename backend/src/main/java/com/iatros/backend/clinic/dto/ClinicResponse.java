package com.iatros.backend.clinic.dto;

import java.util.UUID;

/** The caller's clinic (api.md §6). Profile and settings carry separate versions because they are edited separately. */
public record ClinicResponse(UUID id, String name, String email, String phone, String address, long version,
		Settings settings) {

	/** {@code weekStartDay} is ISO-8601: 1 = Monday … 7 = Sunday. */
	public record Settings(String timeZone, int defaultAppointmentMinutes, String defaultLanguage, int weekStartDay,
			long version) {
	}

}
