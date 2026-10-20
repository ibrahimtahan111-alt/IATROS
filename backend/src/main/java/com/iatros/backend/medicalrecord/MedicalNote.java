package com.iatros.backend.medicalrecord;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "medical_note")
@SQLRestriction("deleted_at IS NULL")
public class MedicalNote extends ClinicalEntry {

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	protected MedicalNote() {
	}

	public MedicalNote(UUID medicalRecordId, String content, UUID createdBy) {
		super(medicalRecordId, createdBy);
		this.content = content;
	}

	public void update(String content, UUID updatedBy) {
		this.content = content;
		markUpdatedBy(updatedBy);
	}

	public String getContent() {
		return content;
	}

}
