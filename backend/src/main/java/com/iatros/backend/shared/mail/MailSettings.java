package com.iatros.backend.shared.mail;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param transport  {@code smtp} (real delivery through spring.mail.*) or {@code outbox} (development and tests:
 *                   messages are kept in memory and, if {@code outboxDir} is set, written to local files)
 * @param from       sender address
 * @param outboxDir  optional directory for outbox files; must be outside the repository or ignored by Git
 * @param appBaseUrl public URL of the frontend, used to build links in emails
 */
@ConfigurationProperties("iatros.mail")
public record MailSettings(
		@DefaultValue("smtp") String transport,
		@DefaultValue("no-reply@iatros.local") String from,
		Path outboxDir,
		@DefaultValue("http://localhost:5173") String appBaseUrl) {

	public boolean usesOutbox() {
		return "outbox".equalsIgnoreCase(transport);
	}

}
