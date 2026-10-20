package com.iatros.backend.dashboard.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.AppointmentType;
import com.iatros.backend.appointment.dto.NameRef;

/**
 * One of today's appointments on a dashboard (api.md §22).
 *
 * @param alerts clinical alerts for the patient; only on the doctor dashboard and only for patients the doctor is
 * authorized for, otherwise null
 */
public record ScheduleItem(UUID id, Instant startAt, Instant endAt, NameRef patient, NameRef doctor,
		AppointmentType type, AppointmentStatus status, ClinicalAlerts alerts) {

	/** Names only: severe allergies and active conditions, for the doctor about to see the patient. */
	public record ClinicalAlerts(List<String> severeAllergies, List<String> activeConditions) {
	}

}
