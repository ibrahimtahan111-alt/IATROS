package com.iatros.backend.shared.mail;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Delivers through SMTP on a background executor, so request latency does not depend on the mail server
 * and does not reveal whether an email was sent (e.g. for forgot-password).
 */
class SmtpEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

	private final JavaMailSender mailSender;
	private final Executor executor;
	private final String from;

	SmtpEmailSender(JavaMailSender mailSender, Executor executor, String from) {
		this.mailSender = mailSender;
		this.executor = executor;
		this.from = from;
	}

	@Override
	public void send(EmailMessage message) {
		executor.execute(() -> {
			SimpleMailMessage mail = new SimpleMailMessage();
			mail.setFrom(from);
			mail.setTo(message.to());
			mail.setSubject(message.subject());
			mail.setText(message.body());
			try {
				mailSender.send(mail);
			}
			catch (MailException ex) {
				log.error("Email delivery failed: {}", ex.getClass().getSimpleName());
			}
		});
	}

}
