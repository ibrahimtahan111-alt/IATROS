package com.iatros.backend.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.iatros.backend.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Immutable record of a sensitive action (security.md §23). Metadata must never contain medical content,
 * passwords, or tokens. The database rejects updates and deletes.
 */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEvent extends BaseEntity {

	@Column(name = "clinic_id", updatable = false)
	private UUID clinicId;

	@Column(name = "actor_user_id", updatable = false)
	private UUID actorUserId;

	@Column(nullable = false, length = 60, updatable = false)
	private String action;

	@Column(name = "entity_type", nullable = false, length = 60, updatable = false)
	private String entityType;

	@Column(name = "entity_id", updatable = false)
	private UUID entityId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false, columnDefinition = "jsonb")
	private Map<String, Object> metadata = Map.of();

	protected AuditEvent() {
	}

	public AuditEvent(UUID clinicId, UUID actorUserId, String action, String entityType, UUID entityId,
			Instant occurredAt, Map<String, Object> metadata) {
		this.clinicId = clinicId;
		this.actorUserId = actorUserId;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.occurredAt = occurredAt;
		this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}

	public UUID getClinicId() {
		return clinicId;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public String getAction() {
		return action;
	}

	public String getEntityType() {
		return entityType;
	}

	public UUID getEntityId() {
		return entityId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

}
