package com.iatros.backend.shared.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/** Entity with creation/update timestamps but no optimistic locking (configuration and link rows). */
@MappedSuperclass
public abstract class TimestampedEntity extends BaseEntity {

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected TimestampedEntity() {
	}

	protected TimestampedEntity(UUID id) {
		super(id);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
