package com.iatros.backend.activity.dto;

import java.time.Instant;
import java.util.UUID;

import com.iatros.backend.activity.ActivityCategory;
import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.dto.NameRef;

/**
 * One activity feed entry (api.md §24). Built from the audit trail, so it carries identifiers, names, and codes
 * only, never notes or medical content. {@code patient} is null when the caller may not see that patient (D-039).
 *
 * @param entityType the audited entity, e.g. {@code ALLERGY} for a medical-record entry
 * @param staff the team member a {@code TEAM} event is about
 * @param status the new status of an {@code APPOINTMENT_STATUS_CHANGED} event
 */
public record ActivityItemResponse(UUID id, Instant occurredAt, String action, ActivityCategory category,
		NameRef actor, String entityType, NameRef patient, NameRef doctor, NameRef staff, UUID appointmentId,
		AppointmentStatus status) {
}
