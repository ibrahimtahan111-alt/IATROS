package com.iatros.backend.support;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

/**
 * Sends a CSRF token the way the SPA does: the same value in the XSRF-TOKEN cookie and the X-XSRF-TOKEN header.
 * Use this instead of Spring Security Test's {@code csrf()}, which permanently replaces the shared filter's
 * cookie repository with a session-based one and makes CSRF behaviour depend on test order.
 */
public final class SpaCsrf {

	public static final String COOKIE = "XSRF-TOKEN";
	public static final String HEADER = "X-XSRF-TOKEN";

	private SpaCsrf() {
	}

	public static RequestPostProcessor spaCsrf() {
		return request -> {
			String token = UUID.randomUUID().toString();
			Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
			request.setCookies(Stream.concat(Arrays.stream(existing), Stream.of(new Cookie(COOKIE, token)))
				.toArray(Cookie[]::new));
			request.addHeader(HEADER, token);
			return request;
		};
	}

}
