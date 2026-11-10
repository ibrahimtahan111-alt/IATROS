package com.iatros.backend.dev;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Daily demo data is a development-only job (decisions.md D-017). */
@Configuration
@Profile("dev")
@EnableScheduling
class DevScheduling {
}
