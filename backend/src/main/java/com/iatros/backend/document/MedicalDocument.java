package com.iatros.backend.document;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.iatros.backend.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Metadata of an uploaded medical document. The bytes live in document storage under {@code storageKey},
 * which is server-generated and never derived from the client file name (decisions.md D-032).
 */
@Entity
@Table(name = "document")
public class MedicalDocument extends BaseEntity {

	@Column(name = "clinic_id", nullable = false, updatable = false)
	private UUID clinicId;

	@Column(name = "medical_record_id", nullable = false, updatable = false)
	private UUID medicalRecordId;

	@Column(name = "consultation_id", updatable = false)
	private UUID consultationId;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 100, updatable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false, updatable = false)
	private long sizeBytes;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "checksum_sha256", nullable = false, length = 64, updatable = false)
	private String checksumSha256;

	@Column(name = "storage_key", nullable = false, updatable = false)
	private String storageKey;

	@Column(name = "uploaded_by", nullable = false, updatable = false)
	private UUID uploadedBy;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "archived_by")
	private UUID archivedBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected MedicalDocument() {
	}

	public MedicalDocument(UUID clinicId, UUID medicalRecordId, UUID consultationId, String fileName,
			String contentType, long sizeBytes, String checksumSha256, String storageKey, UUID uploadedBy) {
		this.clinicId = clinicId;
		this.medicalRecordId = medicalRecordId;
		this.consultationId = consultationId;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.checksumSha256 = checksumSha256;
		this.storageKey = storageKey;
		this.uploadedBy = uploadedBy;
	}

	public void rename(String fileName) {
		this.fileName = fileName;
	}

	public void archive(Instant at, UUID by) {
		this.archivedAt = at;
		this.archivedBy = by;
	}

	public boolean isArchived() {
		return archivedAt != null;
	}

	public UUID getClinicId() {
		return clinicId;
	}

	public UUID getMedicalRecordId() {
		return medicalRecordId;
	}

	public UUID getConsultationId() {
		return consultationId;
	}

	public String getFileName() {
		return fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getChecksumSha256() {
		return checksumSha256;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public UUID getUploadedBy() {
		return uploadedBy;
	}

	public Instant getArchivedAt() {
		return archivedAt;
	}

	public UUID getArchivedBy() {
		return archivedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
