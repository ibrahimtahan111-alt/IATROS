package com.iatros.backend.audit;

/** Stable audit action codes. Stored in the database; never translated or renamed. */
public final class AuditActions {

	public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
	public static final String LOGIN_FAILED = "LOGIN_FAILED";
	public static final String LOGOUT = "LOGOUT";
	public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
	public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
	public static final String PASSWORD_RESET = "PASSWORD_RESET";
	public static final String INVITATION_SENT = "INVITATION_SENT";
	public static final String INVITATION_ACCEPTED = "INVITATION_ACCEPTED";
	public static final String STAFF_CREATED = "STAFF_CREATED";
	public static final String STAFF_UPDATED = "STAFF_UPDATED";
	public static final String STAFF_DEACTIVATED = "STAFF_DEACTIVATED";
	public static final String STAFF_REACTIVATED = "STAFF_REACTIVATED";
	public static final String CLINIC_PROVISIONED = "CLINIC_PROVISIONED";
	public static final String CLINIC_UPDATED = "CLINIC_UPDATED";
	public static final String CLINIC_SETTINGS_UPDATED = "CLINIC_SETTINGS_UPDATED";
	public static final String DOCTOR_PROFILE_UPDATED = "DOCTOR_PROFILE_UPDATED";
	public static final String SCHEDULE_UPDATED = "SCHEDULE_UPDATED";
	public static final String SCHEDULE_EXCEPTION_CREATED = "SCHEDULE_EXCEPTION_CREATED";
	public static final String SCHEDULE_EXCEPTION_DELETED = "SCHEDULE_EXCEPTION_DELETED";
	public static final String PATIENT_CREATED = "PATIENT_CREATED";
	public static final String PATIENT_UPDATED = "PATIENT_UPDATED";
	public static final String PATIENT_ARCHIVED = "PATIENT_ARCHIVED";
	public static final String PATIENT_RESTORED = "PATIENT_RESTORED";
	public static final String PATIENT_PRIMARY_DOCTOR_CHANGED = "PATIENT_PRIMARY_DOCTOR_CHANGED";
	public static final String PATIENT_DOCTOR_AUTHORIZED = "PATIENT_DOCTOR_AUTHORIZED";
	public static final String PATIENT_DOCTOR_REMOVED = "PATIENT_DOCTOR_REMOVED";
	public static final String MEDICAL_ENTRY_CREATED = "MEDICAL_ENTRY_CREATED";
	public static final String MEDICAL_ENTRY_UPDATED = "MEDICAL_ENTRY_UPDATED";
	public static final String MEDICAL_ENTRY_DELETED = "MEDICAL_ENTRY_DELETED";
	public static final String CONSULTATION_CREATED = "CONSULTATION_CREATED";
	public static final String CONSULTATION_UPDATED = "CONSULTATION_UPDATED";
	public static final String APPOINTMENT_CREATED = "APPOINTMENT_CREATED";
	public static final String APPOINTMENT_UPDATED = "APPOINTMENT_UPDATED";
	public static final String APPOINTMENT_RESCHEDULED = "APPOINTMENT_RESCHEDULED";
	public static final String APPOINTMENT_STATUS_CHANGED = "APPOINTMENT_STATUS_CHANGED";
	public static final String APPOINTMENT_CANCELLED = "APPOINTMENT_CANCELLED";
	public static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";
	public static final String DOCUMENT_ARCHIVED = "DOCUMENT_ARCHIVED";
	/** Viewing or downloading a document's file. A record of access, not of change: never in the activity feed. */
	public static final String DOCUMENT_VIEWED = "DOCUMENT_VIEWED";

	public static final String ENTITY_USER = "USER";
	public static final String ENTITY_CLINIC = "CLINIC";
	public static final String ENTITY_DOCTOR = "DOCTOR";
	public static final String ENTITY_SCHEDULE_EXCEPTION = "SCHEDULE_EXCEPTION";
	public static final String ENTITY_PATIENT = "PATIENT";
	public static final String ENTITY_ALLERGY = "ALLERGY";
	public static final String ENTITY_MEDICAL_CONDITION = "MEDICAL_CONDITION";
	public static final String ENTITY_MEDICATION = "MEDICATION";
	public static final String ENTITY_MEDICAL_HISTORY_ENTRY = "MEDICAL_HISTORY_ENTRY";
	public static final String ENTITY_MEDICAL_NOTE = "MEDICAL_NOTE";
	public static final String ENTITY_CONSULTATION = "CONSULTATION";
	public static final String ENTITY_APPOINTMENT = "APPOINTMENT";
	public static final String ENTITY_DOCUMENT = "DOCUMENT";

	private AuditActions() {
	}

}
