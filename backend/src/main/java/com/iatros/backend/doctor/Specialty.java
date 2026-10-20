package com.iatros.backend.doctor;

import com.iatros.backend.shared.persistence.TimestampedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Controlled reference data, seeded by migration. The UI translates the stable code. */
@Entity
@Table(name = "specialty")
public class Specialty extends TimestampedEntity {

	@Column(nullable = false, length = 50, updatable = false)
	private String code;

	@Column(nullable = false)
	private boolean active = true;

	protected Specialty() {
	}

	public String getCode() {
		return code;
	}

	public boolean isActive() {
		return active;
	}

}
