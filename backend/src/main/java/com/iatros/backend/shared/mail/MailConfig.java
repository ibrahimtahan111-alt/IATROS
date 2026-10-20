package com.iatros.backend.shared.mail;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@EnableConfigurationProperties(MailSettings.class)
public class MailConfig {

	@Bean
	EmailSender emailSender(MailSettings settings, ObjectProvider<JavaMailSender> mailSender, Clock clock) {
		if (settings.usesOutbox()) {
			return new OutboxEmailSender(settings.outboxDir(), clock);
		}
		JavaMailSender smtp = mailSender.getIfAvailable();
		if (smtp == null) {
			throw new IllegalStateException(
					"iatros.mail.transport=smtp requires spring.mail.host to be configured");
		}
		Executor executor = Executors.newVirtualThreadPerTaskExecutor();
		return new SmtpEmailSender(smtp, executor, settings.from());
	}

}
