package com.iatros.backend.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores documents as files under one directory, in subdirectories named after the key's last two characters so no
 * directory grows too large. Files are written to a temporary name first, so a partial upload is never visible
 * under its key.
 */
class LocalDocumentStorage implements DocumentStorage {

	private final Path root;

	LocalDocumentStorage(Path root) throws IOException {
		this.root = Files.createDirectories(root.toAbsolutePath().normalize());
	}

	@Override
	public void store(String key, InputStream content) throws IOException {
		Path target = pathOf(key);
		Files.createDirectories(target.getParent());
		Path temporary = Files.createTempFile(target.getParent(), key, ".part");
		try {
			Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
		}
		finally {
			Files.deleteIfExists(temporary);
		}
	}

	@Override
	public InputStream open(String key) throws IOException {
		return Files.newInputStream(pathOf(key));
	}

	@Override
	public void delete(String key) throws IOException {
		Files.deleteIfExists(pathOf(key));
	}

	/** Keys are UUIDs; anything else is refused, so a key can never point outside the root. */
	private Path pathOf(String key) {
		String canonical = UUID.fromString(key).toString();
		if (!canonical.equals(key)) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		return root.resolve(key.substring(key.length() - 2)).resolve(key);
	}

}
