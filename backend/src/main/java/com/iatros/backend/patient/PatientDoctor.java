package com.iatros.backend.patient;

import java.util.UUID;

import com.iatros.backend.shared.persistence.TimestampedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Grants a doctor access to a patient. At most one PRIMARY relationship per patient. */
@Entity
@Table(name = "patient_doctor")
public class PatientDoctor extends TimestampedEntity {

	@Column(name = "clinic_id", nullable = false, updatable = false)
	private UUID clinicId;

	@Column(name = "patient_id", nullable = false, updatable = false)
	private UUID patientId;

	@Column(name = "doctor_id", nullable = false, updatable = false)
	private UUID doctorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "relationship_type", nullable = false, length = 20)
	private DoctorRelationshipType relationshipType;

	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	protected PatientDoctor() {
	}

	public PatientDoctor(UUID clinicId, UUID patientId, UUID doctorId, DoctorRelationshipType relationshipType,
			UUID createdBy) {
		this.clinicId = clinicId;
		this.patientId = patientId;
		this.doctorId = doctorId;
		this.relationshipType = relationshipType;
		this.createdBy = createdBy;
	}

	public void changeRelationshipType(DoctorRelationshipType relationshipType) {
		this.relationshipType = relationshipType;
	}

	public UUID getClinicId() {
		return clinicId;
	}

	public UUID getPatientId() {
		return patientId;
	}

	public UUID getDoctorId() {
		return doctorId;
	}

	public DoctorRelationshipType getRelationshipType() {
		return relationshipType;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

}
