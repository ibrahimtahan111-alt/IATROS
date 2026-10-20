package com.iatros.backend.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidV7Tests {

	@Test
	void generatesVersion7WithRfcVariant() {
		UUID id = UuidV7.generate();

		assertThat(id.version()).isEqualTo(7);
		assertThat(id.variant()).isEqualTo(2);
	}

	@Test
	void embedsTheTimestampInTheMostSignificantBits() {
		long millis = 1_800_000_000_000L;

		UUID id = UuidV7.fromTimestamp(millis);

		assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(millis);
	}

	@Test
	void laterTimestampsSortAfterEarlierOnes() {
		UUID earlier = UuidV7.fromTimestamp(1_800_000_000_000L);
		UUID later = UuidV7.fromTimestamp(1_800_000_000_001L);

		assertThat(earlier.toString()).isLessThan(later.toString());
	}

	@Test
	void idsAreUniqueWithinTheSameMillisecond() {
		Set<UUID> ids = new HashSet<>();
		for (int i = 0; i < 10_000; i++) {
			ids.add(UuidV7.fromTimestamp(1_800_000_000_000L));
		}

		assertThat(ids).hasSize(10_000);
	}

}
