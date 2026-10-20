package com.iatros.backend.security;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id by default, behind a delegating encoder so the algorithm or its cost can change later without
 * forcing password resets (decisions.md D-013). Parameters follow the OWASP baseline: 19 MiB, 2 iterations.
 */
@Configuration
public class PasswordEncoderConfig {

	static final String ARGON2 = "argon2";

	@Bean
	PasswordEncoder passwordEncoder() {
		var argon2 = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
		return new DelegatingPasswordEncoder(ARGON2, Map.of(
				ARGON2, argon2,
				"bcrypt", new BCryptPasswordEncoder()));
	}

}
