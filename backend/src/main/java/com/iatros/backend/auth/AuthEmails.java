package com.iatros.backend.auth;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.iatros.backend.clinic.ClinicSettingsRepository;
import com.iatros.backend.shared.i18n.Language;
import com.iatros.backend.shared.mail.EmailMessage;
import com.iatros.backend.shared.mail.MailSettings;
import com.iatros.backend.user.AppUser;

/**
 * Composes authentication emails in the recipient's language (user preference, then clinic default).
 * Tokens travel in the URL fragment (#token=…), which browsers never send to servers or in Referer headers.
 */
@Component
class AuthEmails {

	private final MessageSource messages;
	private final MailSettings settings;
	private final ClinicSettingsRepository clinicSettings;

	AuthEmails(MessageSource messages, MailSettings settings, ClinicSettingsRepository clinicSettings) {
		this.messages = messages;
		this.settings = settings;
		this.clinicSettings = clinicSettings;
	}

	EmailMessage passwordReset(AppUser user, String rawToken) {
		Locale locale = localeFor(user);
		String link = link("/reset-password", rawToken);
		return new EmailMessage(user.getEmail(),
				messages.getMessage("mail.passwordReset.subject", null, locale),
				messages.getMessage("mail.passwordReset.body", new Object[] { user.getFirstName(), link }, locale));
	}

	EmailMessage invitation(AppUser user, String clinicName, String rawToken) {
		Locale locale = localeFor(user);
		String link = link("/accept-invitation", rawToken);
		return new EmailMessage(user.getEmail(),
				messages.getMessage("mail.invitation.subject", new Object[] { clinicName }, locale),
				messages.getMessage("mail.invitation.body", new Object[] { user.getFirstName(), clinicName, link },
						locale));
	}

	private String link(String path, String rawToken) {
		return settings.appBaseUrl().replaceAll("/+$", "") + path + "#token=" + rawToken;
	}

	private Locale localeFor(AppUser user) {
		Language language = user.getPreferredLanguage() != null ? user.getPreferredLanguage()
				: clinicSettings.findById(user.getClinicId()).map(s -> s.getDefaultLanguage()).orElse(Language.EN);
		return Locale.forLanguageTag(language.code());
	}

}
