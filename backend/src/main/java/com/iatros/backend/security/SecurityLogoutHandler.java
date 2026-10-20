package com.iatros.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Audits logouts. Session invalidation and context clearing are done by Spring Security's own handlers. */
@Component
class SecurityLogoutHandler implements LogoutHandler {

	private final AuditService audit;

	SecurityLogoutHandler(AuditService audit) {
		this.audit = audit;
	}

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			audit.record(user.clinicId(), user.userId(), AuditActions.LOGOUT, AuditActions.ENTITY_USER, user.userId());
		}
	}

}
