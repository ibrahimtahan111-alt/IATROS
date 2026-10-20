package com.iatros.backend.shared.mail;

/** A plain-text transactional email. The body may contain single-use links and must never be logged. */
public record EmailMessage(String to, String subject, String body) {

	@Override
	public String toString() {
		return "EmailMessage[subject=" + subject + "]";
	}

}
