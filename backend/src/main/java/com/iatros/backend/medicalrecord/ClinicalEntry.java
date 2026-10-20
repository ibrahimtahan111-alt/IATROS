package com.iatros.backend.medicalrecord;

import java.time.Instant;
import java.util.UUID;

import com.iatros.backend.shared.persistence.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Common shape of medical-record entries (allergies, conditions, medications, history, notes).
 * Entries are soft-deleted and attributed to their author (decisions.md D-030).
 * Subclasses use {@code @SQLRestriction("deleted_at IS NULL")} so deleted entries never appear in normal reads.
 */
@MappedSuperclass
public abstract class ClinicalEntry extends AuditedEntity {

	@Column(name = "medical_record_id", nullable = false, updatable = false)
	private UUID medicalRecordId;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "deleted_by")
	private UUID deletedBy;

	protected ClinicalEntry() {
	}

	protected ClinicalEntry(UUID medicalRecordId, UUID createdBy) {
		this.medicalRecordId = medicalRecordId;
		this.createdBy = createdBy;
	}

	protected void markUpdatedBy(UUID userId) {
		this.updatedBy = userId;
	}

	public void softDelete(Instant at, UUID by) {
		this.deletedAt = at;
		this.deletedBy = by;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public UUID getMedicalRecordId() {
		return medicalRecordId;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public UUID getUpdatedBy() {
		return updatedBy;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public UUID getDeletedBy() {
		return deletedBy;
	}

}
