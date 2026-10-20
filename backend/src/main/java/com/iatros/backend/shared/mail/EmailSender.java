package com.iatros.backend.shared.mail;

/** Sends transactional emails (decisions.md D-015). Implementations must not log message bodies. */
public interface EmailSender {

	void send(EmailMessage message);

}
