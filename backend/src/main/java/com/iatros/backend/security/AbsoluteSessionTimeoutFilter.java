package com.iatros.backend.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Ends sessions older than the absolute lifetime (12 hours, decisions.md D-010) regardless of activity.
 * The idle timeout is handled by the session store itself.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

	public static final String AUTHENTICATED_AT = "iatros.authenticatedAt";

	private final Clock clock;
	private final Duration maxLifetime;

	public AbsoluteSessionTimeoutFilter(Clock clock, Duration maxLifetime) {
		this.clock = clock;
		this.maxLifetime = maxLifetime;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null && session.getAttribute(AUTHENTICATED_AT) instanceof Instant authenticatedAt
				&& Instant.now(clock).isAfter(authenticatedAt.plus(maxLifetime))) {
			session.invalidate();
			SecurityContextHolder.clearContext();
		}
		chain.doFilter(request, response);
	}

}
