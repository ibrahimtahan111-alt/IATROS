package com.iatros.backend.medicalrecord;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "medical_condition")
@SQLRestriction("deleted_at IS NULL")
public class MedicalCondition extends ClinicalEntry {

	@Column(nullable = false, length = 200)
	private String name;

	@Column(columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ConditionStatus status = ConditionStatus.ACTIVE;

	@Column(name = "diagnosed_on")
	private LocalDate diagnosedOn;

	protected MedicalCondition() {
	}

	public MedicalCondition(UUID clinicId, UUID medicalRecordId, String name, UUID createdBy) {
		super(clinicId, medicalRecordId, createdBy);
		this.name = name;
	}

	public void update(String name, String description, ConditionStatus status, LocalDate diagnosedOn, UUID updatedBy) {
		this.name = name;
		this.description = description;
		this.status = status;
		this.diagnosedOn = diagnosedOn;
		markUpdatedBy(updatedBy);
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public ConditionStatus getStatus() {
		return status;
	}

	public LocalDate getDiagnosedOn() {
		return diagnosedOn;
	}

}
