package com.iatros.backend.shared.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development/test transport: keeps sent messages in memory and optionally writes each one to a text file,
 * so invitation and reset links can be opened locally without an SMTP server. Never used in production.
 */
public class OutboxEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(OutboxEmailSender.class);
	private static final int MAX_KEPT = 100;

	private final Path directory;
	private final Clock clock;
	private final List<EmailMessage> sent = new ArrayList<>();

	OutboxEmailSender(Path directory, Clock clock) {
		this.directory = directory;
		this.clock = clock;
	}

	@Override
	public synchronized void send(EmailMessage message) {
		sent.add(message);
		if (sent.size() > MAX_KEPT) {
			sent.removeFirst();
		}
		if (directory != null) {
			writeFile(message);
		}
	}

	public synchronized List<EmailMessage> sentMessages() {
		return List.copyOf(sent);
	}

	public synchronized void clear() {
		sent.clear();
	}

	private void writeFile(EmailMessage message) {
		String name = Instant.now(clock).toString().replace(':', '-') + "-" + UUID.randomUUID() + ".txt";
		String content = "To: " + message.to() + "\nSubject: " + message.subject() + "\n\n" + message.body() + "\n";
		try {
			Files.createDirectories(directory);
			Path file = directory.resolve(name);
			Files.writeString(file, content, StandardCharsets.UTF_8);
			log.info("Development email written to {}", file.toAbsolutePath());
		}
		catch (IOException ex) {
			log.warn("Could not write development email: {}", ex.getClass().getSimpleName());
		}
	}

}
