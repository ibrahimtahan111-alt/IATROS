package com.iatros.backend.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates time-ordered UUID version 7 identifiers (RFC 9562).
 * Time ordering keeps primary-key indexes compact; the random part keeps IDs unguessable.
 */
public final class UuidV7 {

	private static final SecureRandom RANDOM = new SecureRandom();

	private UuidV7() {
	}

	public static UUID generate() {
		return fromTimestamp(System.currentTimeMillis());
	}

	static UUID fromTimestamp(long epochMillis) {
		long randA = RANDOM.nextInt(1 << 12);
		long randB = RANDOM.nextLong();

		long mostSignificant = (epochMillis << 16) | (0x7L << 12) | randA;
		long leastSignificant = (randB & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
		return new UUID(mostSignificant, leastSignificant);
	}

}
