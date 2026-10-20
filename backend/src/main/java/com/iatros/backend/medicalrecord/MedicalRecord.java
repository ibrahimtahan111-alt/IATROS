package com.iatros.backend.medicalrecord;

import java.util.UUID;

import com.iatros.backend.shared.persistence.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Every patient has exactly one medical record, created together with the patient. */
@Entity
@Table(name = "medical_record")
public class MedicalRecord extends AuditedEntity {

	@Column(name = "clinic_id", nullable = false, updatable = false)
	private UUID clinicId;

	@Column(name = "patient_id", nullable = false, updatable = false)
	private UUID patientId;

	protected MedicalRecord() {
	}

	public MedicalRecord(UUID clinicId, UUID patientId) {
		this.clinicId = clinicId;
		this.patientId = patientId;
	}

	public UUID getClinicId() {
		return clinicId;
	}

	public UUID getPatientId() {
		return patientId;
	}

}
