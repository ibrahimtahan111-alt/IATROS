package com.iatros.backend.security;

import java.util.UUID;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Revokes a user's server-side sessions, e.g. after a password change, reset, deactivation, or role change.
 * Sessions are indexed by principal name, which is the user ID (see AuthenticatedUser).
 */
@Service
public class UserSessionService {

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	public UserSessionService(FindByIndexNameSessionRepository<? extends Session> sessions) {
		this.sessions = sessions;
	}

	public void invalidateAll(UUID userId) {
		invalidateAllExcept(userId, null);
	}

	/** Invalidates every session of the user except {@code keepSessionId} (the caller's own session), if given. */
	public void invalidateAllExcept(UUID userId, String keepSessionId) {
		sessions.findByPrincipalName(userId.toString()).keySet().stream()
			.filter(sessionId -> !sessionId.equals(keepSessionId))
			.forEach(sessions::deleteById);
	}

}
