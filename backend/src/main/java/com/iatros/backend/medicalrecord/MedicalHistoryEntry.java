package com.iatros.backend.medicalrecord;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "medical_history_entry")
@SQLRestriction("deleted_at IS NULL")
public class MedicalHistoryEntry extends ClinicalEntry {

	@Column(nullable = false, columnDefinition = "text")
	private String description;

	@Column(name = "occurred_on")
	private LocalDate occurredOn;

	@Column(columnDefinition = "text")
	private String notes;

	protected MedicalHistoryEntry() {
	}

	public MedicalHistoryEntry(UUID clinicId, UUID medicalRecordId, String description, UUID createdBy) {
		super(clinicId, medicalRecordId, createdBy);
		this.description = description;
	}

	public void update(String description, LocalDate occurredOn, String notes, UUID updatedBy) {
		this.description = description;
		this.occurredOn = occurredOn;
		this.notes = notes;
		markUpdatedBy(updatedBy);
	}

	public String getDescription() {
		return description;
	}

	public LocalDate getOccurredOn() {
		return occurredOn;
	}

	public String getNotes() {
		return notes;
	}

}
