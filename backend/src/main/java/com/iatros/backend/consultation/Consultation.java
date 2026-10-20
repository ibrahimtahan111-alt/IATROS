package com.iatros.backend.consultation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.iatros.backend.shared.persistence.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** The clinical encounter, part of a medical record. Optionally linked to one appointment. */
@Entity
@Table(name = "consultation")
public class Consultation extends AuditedEntity {

	@Column(name = "clinic_id", nullable = false, updatable = false)
	private UUID clinicId;

	@Column(name = "medical_record_id", nullable = false, updatable = false)
	private UUID medicalRecordId;

	@Column(name = "doctor_id", nullable = false, updatable = false)
	private UUID doctorId;

	@Column(name = "appointment_id", updatable = false)
	private UUID appointmentId;

	@Column(name = "consulted_at", nullable = false)
	private Instant consultedAt;

	@Column(length = 500)
	private String reason;

	@Column(columnDefinition = "text")
	private String diagnosis;

	@Column(name = "clinical_notes", columnDefinition = "text")
	private String clinicalNotes;

	@Column(name = "treatment_notes", columnDefinition = "text")
	private String treatmentNotes;

	@Column(name = "follow_up_notes", columnDefinition = "text")
	private String followUpNotes;

	@Column(name = "follow_up_on")
	private LocalDate followUpOn;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "updated_by")
	private UUID updatedBy;

	protected Consultation() {
	}

	public Consultation(UUID clinicId, UUID medicalRecordId, UUID doctorId, UUID appointmentId, Instant consultedAt,
			UUID createdBy) {
		this.clinicId = clinicId;
		this.medicalRecordId = medicalRecordId;
		this.doctorId = doctorId;
		this.appointmentId = appointmentId;
		this.consultedAt = consultedAt;
		this.createdBy = createdBy;
	}

	public void updateClinicalContent(Instant consultedAt, String reason, String diagnosis, String clinicalNotes,
			String treatmentNotes, String followUpNotes, LocalDate followUpOn, UUID by) {
		this.consultedAt = consultedAt;
		this.reason = reason;
		this.diagnosis = diagnosis;
		this.clinicalNotes = clinicalNotes;
		this.treatmentNotes = treatmentNotes;
		this.followUpNotes = followUpNotes;
		this.followUpOn = followUpOn;
		this.updatedBy = by;
	}

	public UUID getClinicId() {
		return clinicId;
	}

	public UUID getMedicalRecordId() {
		return medicalRecordId;
	}

	public UUID getDoctorId() {
		return doctorId;
	}

	public UUID getAppointmentId() {
		return appointmentId;
	}

	public Instant getConsultedAt() {
		return consultedAt;
	}

	public String getReason() {
		return reason;
	}

	public String getDiagnosis() {
		return diagnosis;
	}

	public String getClinicalNotes() {
		return clinicalNotes;
	}

	public String getTreatmentNotes() {
		return treatmentNotes;
	}

	public String getFollowUpNotes() {
		return followUpNotes;
	}

	public LocalDate getFollowUpOn() {
		return followUpOn;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public UUID getUpdatedBy() {
		return updatedBy;
	}

}
