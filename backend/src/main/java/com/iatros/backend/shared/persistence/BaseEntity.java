package com.iatros.backend.shared.persistence;

import java.util.Objects;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.data.domain.Persistable;

import com.iatros.backend.shared.id.UuidV7;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

/**
 * Base class for entities with application-assigned UUID v7 identifiers (decisions.md D-003).
 * The ID exists from construction, so equality is stable before and after persistence.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@Transient
	private boolean isNew = true;

	protected BaseEntity() {
		this.id = UuidV7.generate();
	}

	protected BaseEntity(UUID id) {
		this.id = Objects.requireNonNull(id, "id");
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostPersist
	@PostLoad
	void markNotNew() {
		this.isNew = false;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
			return false;
		}
		return id.equals(((BaseEntity) other).getId());
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

}
