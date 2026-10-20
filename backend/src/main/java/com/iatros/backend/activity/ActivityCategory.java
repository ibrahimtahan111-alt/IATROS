package com.iatros.backend.activity;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.iatros.backend.audit.AuditActions;

/**
 * Activity feed types (api.md §24, decisions.md D-039). Each groups audit actions; actions in no category (sign-ins,
 * sign-outs, password events, document views) are security or access records, not clinic activity, and never appear
 * in the feed.
 */
public enum ActivityCategory {

	APPOINTMENTS(AuditActions.APPOINTMENT_CREATED, AuditActions.APPOINTMENT_UPDATED,
			AuditActions.APPOINTMENT_RESCHEDULED, AuditActions.APPOINTMENT_STATUS_CHANGED,
			AuditActions.APPOINTMENT_CANCELLED),
	PATIENTS(AuditActions.PATIENT_CREATED, AuditActions.PATIENT_UPDATED, AuditActions.PATIENT_ARCHIVED,
			AuditActions.PATIENT_RESTORED, AuditActions.PATIENT_PRIMARY_DOCTOR_CHANGED,
			AuditActions.PATIENT_DOCTOR_AUTHORIZED, AuditActions.PATIENT_DOCTOR_REMOVED),
	CLINICAL(AuditActions.MEDICAL_ENTRY_CREATED, AuditActions.MEDICAL_ENTRY_UPDATED,
			AuditActions.MEDICAL_ENTRY_DELETED, AuditActions.CONSULTATION_CREATED, AuditActions.CONSULTATION_UPDATED,
			AuditActions.DOCUMENT_UPLOADED, AuditActions.DOCUMENT_ARCHIVED),
	SCHEDULES(AuditActions.SCHEDULE_UPDATED, AuditActions.SCHEDULE_EXCEPTION_CREATED,
			AuditActions.SCHEDULE_EXCEPTION_DELETED),
	TEAM(AuditActions.STAFF_CREATED, AuditActions.STAFF_UPDATED, AuditActions.STAFF_DEACTIVATED,
			AuditActions.STAFF_REACTIVATED, AuditActions.INVITATION_SENT, AuditActions.INVITATION_ACCEPTED,
			AuditActions.DOCTOR_PROFILE_UPDATED),
	CLINIC(AuditActions.CLINIC_PROVISIONED, AuditActions.CLINIC_UPDATED, AuditActions.CLINIC_SETTINGS_UPDATED);

	private final List<String> actions;

	ActivityCategory(String... actions) {
		this.actions = List.of(actions);
	}

	public List<String> actions() {
		return actions;
	}

	public static Optional<ActivityCategory> of(String action) {
		return Arrays.stream(values()).filter(category -> category.actions.contains(action)).findFirst();
	}

	/** The actions of the selected categories; all categories when none is selected. */
	public static List<String> actionsOf(Collection<ActivityCategory> selected) {
		Collection<ActivityCategory> categories = selected == null || selected.isEmpty() ? List.of(values())
				: selected;
		return categories.stream().distinct().flatMap(category -> category.actions.stream()).toList();
	}

}
