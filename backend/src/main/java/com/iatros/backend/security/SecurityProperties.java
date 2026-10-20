package com.iatros.backend.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secureCookies      mark session and CSRF cookies Secure; true everywhere except local HTTP development
 * @param sessionIdleTimeout session ends after this much inactivity (decisions.md D-010)
 * @param sessionMaxLifetime absolute session lifetime regardless of activity (decisions.md D-010)
 */
@ConfigurationProperties("iatros.security")
public record SecurityProperties(
		@DefaultValue("true") boolean secureCookies,
		@DefaultValue("30m") Duration sessionIdleTimeout,
		@DefaultValue("12h") Duration sessionMaxLifetime) {
}
