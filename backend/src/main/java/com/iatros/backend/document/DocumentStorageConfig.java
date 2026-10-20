package com.iatros.backend.document;

import java.io.IOException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Local file storage in every environment for now; an S3-compatible implementation replaces it for production. */
@Configuration
@EnableConfigurationProperties(DocumentSettings.class)
class DocumentStorageConfig {

	@Bean
	DocumentStorage documentStorage(DocumentSettings settings) throws IOException {
		if (settings.localDir() == null || settings.localDir().toString().isBlank()) {
			throw new IllegalStateException("iatros.documents.local-dir must be configured");
		}
		return new LocalDocumentStorage(settings.localDir());
	}

}
