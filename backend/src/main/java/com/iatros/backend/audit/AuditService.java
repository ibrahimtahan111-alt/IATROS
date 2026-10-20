package com.iatros.backend.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records sensitive actions (security.md §23). Metadata must never contain medical content, passwords,
 * tokens, or other personal data; identifiers and stable codes only.
 */
@Service
public class AuditService {

	private final AuditEventRepository events;
	private final Clock clock;

	public AuditService(AuditEventRepository events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public void record(UUID clinicId, UUID actorUserId, String action, String entityType, UUID entityId) {
		record(clinicId, actorUserId, action, entityType, entityId, Map.of());
	}

	@Transactional
	public void record(UUID clinicId, UUID actorUserId, String action, String entityType, UUID entityId,
			Map<String, Object> metadata) {
		events.save(new AuditEvent(clinicId, actorUserId, action, entityType, entityId, Instant.now(clock), metadata));
	}

}
