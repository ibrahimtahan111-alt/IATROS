package com.iatros.backend.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.auth.dto.CurrentUserResponse;
import com.iatros.backend.auth.dto.LoginRequest;
import com.iatros.backend.security.AbsoluteSessionTimeoutFilter;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.AuthenticationFailedException;
import com.iatros.backend.user.AppUserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JSON login that establishes a server-side session (decisions.md D-010): authenticate, rotate the session ID
 * and CSRF token, store the security context in the session.
 */
@Service
public class LoginService {

	private final AuthenticationManager authenticationManager;
	private final SessionAuthenticationStrategy sessionStrategy;
	private final SecurityContextRepository contextRepository;
	private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();
	private final CurrentUserService currentUsers;
	private final AppUserRepository users;
	private final AuditService audit;
	private final AuthRateLimits rateLimits;
	private final Clock clock;

	public LoginService(AuthenticationManager authenticationManager, SessionAuthenticationStrategy sessionStrategy,
			SecurityContextRepository contextRepository, CurrentUserService currentUsers, AppUserRepository users,
			AuditService audit, AuthRateLimits rateLimits, Clock clock) {
		this.authenticationManager = authenticationManager;
		this.sessionStrategy = sessionStrategy;
		this.contextRepository = contextRepository;
		this.currentUsers = currentUsers;
		this.users = users;
		this.audit = audit;
		this.rateLimits = rateLimits;
		this.clock = clock;
	}

	public CurrentUserResponse login(LoginRequest login, HttpServletRequest request, HttpServletResponse response) {
		String email = AuthRateLimits.normalize(login.email());
		rateLimits.login(request.getRemoteAddr(), email);

		Authentication authentication;
		try {
			authentication = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(email, login.password()));
		}
		catch (AuthenticationException ex) {
			recordFailure(email);
			throw new AuthenticationFailedException("INVALID_CREDENTIALS", "Login rejected");
		}

		sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = holderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		holderStrategy.setContext(context);
		contextRepository.saveContext(context, request, response);
		request.getSession().setAttribute(AbsoluteSessionTimeoutFilter.AUTHENTICATED_AT, Instant.now(clock));
		issueFreshCsrfCookie(request);

		return currentUsers.recordLogin((AuthenticatedUser) authentication.getPrincipal());
	}

	private void recordFailure(String email) {
		users.findByEmail(email).ifPresentOrElse(
				user -> audit.record(user.getClinicId(), user.getId(), AuditActions.LOGIN_FAILED,
						AuditActions.ENTITY_USER, user.getId()),
				() -> audit.record(null, null, AuditActions.LOGIN_FAILED, AuditActions.ENTITY_USER, null));
	}

	/** The CSRF strategy cleared the old token; reading the new one writes its cookie on this response. */
	private static void issueFreshCsrfCookie(HttpServletRequest request) {
		if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
			token.getToken();
		}
	}

}
