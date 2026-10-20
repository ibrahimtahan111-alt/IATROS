package com.iatros.backend.doctor;

import java.util.UUID;

import com.iatros.backend.shared.persistence.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Doctor-specific profile of a user. The user may be a DOCTOR or the clinic OWNER (decisions.md D-033).
 * Related aggregates are referenced by ID (decisions.md D-009).
 */
@Entity
@Table(name = "doctor")
public class Doctor extends AuditedEntity {

	@Column(name = "clinic_id", nullable = false, updatable = false)
	private UUID clinicId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "primary_specialty_id", nullable = false)
	private UUID primarySpecialtyId;

	@Column(name = "additional_specialty", length = 200)
	private String additionalSpecialty;

	@Column(name = "registration_number", length = 50)
	private String registrationNumber;

	/** Changed only through {@link DoctorRepository#advanceScheduleVersion}, never by dirty checking. */
	@Column(name = "schedule_version", nullable = false, insertable = false, updatable = false)
	private long scheduleVersion;

	protected Doctor() {
	}

	public Doctor(UUID clinicId, UUID userId, UUID primarySpecialtyId) {
		this.clinicId = clinicId;
		this.userId = userId;
		this.primarySpecialtyId = primarySpecialtyId;
	}

	public void updateProfessionalDetails(UUID primarySpecialtyId, String additionalSpecialty, String registrationNumber) {
		this.primarySpecialtyId = primarySpecialtyId;
		this.additionalSpecialty = additionalSpecialty;
		this.registrationNumber = registrationNumber;
	}

	public UUID getClinicId() {
		return clinicId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getPrimarySpecialtyId() {
		return primarySpecialtyId;
	}

	public String getAdditionalSpecialty() {
		return additionalSpecialty;
	}

	public String getRegistrationNumber() {
		return registrationNumber;
	}

	public long getScheduleVersion() {
		return scheduleVersion;
	}

}
