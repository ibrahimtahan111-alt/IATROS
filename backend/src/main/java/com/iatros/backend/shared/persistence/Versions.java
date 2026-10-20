package com.iatros.backend.shared.persistence;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Optimistic locking for edits made from a form: the client sends the version it loaded, and the update is
 * rejected with 409 CONCURRENT_MODIFICATION if the entity changed since (decisions.md D-008).
 */
public final class Versions {

	private Versions() {
	}

	public static void require(AuditedEntity entity, long expectedVersion) {
		if (entity.getVersion() != expectedVersion) {
			throw new ObjectOptimisticLockingFailureException(entity.getClass(), entity.getId());
		}
	}

}
