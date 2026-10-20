package com.iatros.backend.document;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param localDir directory of the local file storage; must be outside the source tree and backed up with the
 *                 database, since document rows reference its files
 * @param maxSize  largest accepted file (decisions.md D-032); the multipart limits must allow it
 */
@ConfigurationProperties("iatros.documents")
public record DocumentSettings(Path localDir, @DefaultValue("20MB") DataSize maxSize) {
}
