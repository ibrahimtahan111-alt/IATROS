package com.iatros.backend.medicalrecord;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Informational medication entry; IATROS V1 is not an e-prescribing system. */
@Entity
@Table(name = "medication")
@SQLRestriction("deleted_at IS NULL")
public class Medication extends ClinicalEntry {

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 100)
	private String dosage;

	@Column(length = 100)
	private String frequency;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(nullable = false)
	private boolean active = true;

	protected Medication() {
	}

	public Medication(UUID medicalRecordId, String name, UUID createdBy) {
		super(medicalRecordId, createdBy);
		this.name = name;
	}

	public void update(String name, String dosage, String frequency, String notes, LocalDate startDate,
			LocalDate endDate, boolean active, UUID updatedBy) {
		this.name = name;
		this.dosage = dosage;
		this.frequency = frequency;
		this.notes = notes;
		this.startDate = startDate;
		this.endDate = endDate;
		this.active = active;
		markUpdatedBy(updatedBy);
	}

	public String getName() {
		return name;
	}

	public String getDosage() {
		return dosage;
	}

	public String getFrequency() {
		return frequency;
	}

	public String getNotes() {
		return notes;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public boolean isActive() {
		return active;
	}

}
