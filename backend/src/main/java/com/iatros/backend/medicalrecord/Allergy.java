package com.iatros.backend.medicalrecord;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "allergy")
@SQLRestriction("deleted_at IS NULL")
public class Allergy extends ClinicalEntry {

	@Column(nullable = false, length = 200)
	private String allergen;

	@Column(length = 500)
	private String reaction;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private AllergySeverity severity;

	@Column(columnDefinition = "text")
	private String notes;

	protected Allergy() {
	}

	public Allergy(UUID medicalRecordId, String allergen, UUID createdBy) {
		super(medicalRecordId, createdBy);
		this.allergen = allergen;
	}

	public void update(String allergen, String reaction, AllergySeverity severity, String notes, UUID updatedBy) {
		this.allergen = allergen;
		this.reaction = reaction;
		this.severity = severity;
		this.notes = notes;
		markUpdatedBy(updatedBy);
	}

	public String getAllergen() {
		return allergen;
	}

	public String getReaction() {
		return reaction;
	}

	public AllergySeverity getSeverity() {
		return severity;
	}

	public String getNotes() {
		return notes;
	}

}
