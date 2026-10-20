package com.iatros.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Session cookie and idle timeout (decisions.md D-010), configured explicitly rather than through
 * server.servlet.session.* so the security-relevant attributes are visible here and verified by tests.
 */
@Configuration
public class SessionConfig {

	public static final String SESSION_COOKIE = "IATROS_SESSION";

	@Bean
	CookieSerializer cookieSerializer(SecurityProperties properties) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(SESSION_COOKIE);
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setUseSecureCookie(properties.secureCookies());
		serializer.setSameSite("Lax");
		return serializer;
	}

	@Bean
	SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionIdleTimeout(SecurityProperties properties) {
		return repository -> repository.setDefaultMaxInactiveInterval(properties.sessionIdleTimeout());
	}

}
