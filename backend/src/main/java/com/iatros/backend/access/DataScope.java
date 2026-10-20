package com.iatros.backend.access;

import java.util.Optional;
import java.util.UUID;

/**
 * The rows a user may read in a list, search, or KPI query (roles-and-permissions.md §11–12). Every query is
 * limited to {@code clinicId}; when {@code doctorId} is present it is further limited to that doctor's data
 * (their appointments, or patients they are authorized for).
 */
public record DataScope(UUID clinicId, Optional<UUID> doctorId) {

	public static DataScope clinicWide(UUID clinicId) {
		return new DataScope(clinicId, Optional.empty());
	}

	public static DataScope doctor(UUID clinicId, UUID doctorId) {
		return new DataScope(clinicId, Optional.of(doctorId));
	}

	public boolean isClinicWide() {
		return doctorId.isEmpty();
	}

}
