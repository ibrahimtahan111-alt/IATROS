package com.iatros.backend.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.iatros.backend.shared.error.BadRequestException;

class PageRequestsTests {

	private static final SortWhitelist PATIENTS = SortWhitelist.of(
			Sort.by("lastName", "firstName"),
			Map.of("lastName", "lastName", "createdAt", "createdAt"));

	@Test
	void appliesDefaultsAndStableTieBreaker() {
		Pageable pageable = PageRequests.of(null, null, null, PATIENTS);

		assertThat(pageable.getPageNumber()).isZero();
		assertThat(pageable.getPageSize()).isEqualTo(PageRequests.DEFAULT_SIZE);
		assertThat(pageable.getSort()).isEqualTo(Sort.by("lastName", "firstName", "id"));
	}

	@Test
	void clampsPageSizeToTheMaximum() {
		assertThat(PageRequests.of(0, 10_000, null, PATIENTS).getPageSize()).isEqualTo(PageRequests.MAX_SIZE);
	}

	@Test
	void parsesWhitelistedSortFieldsAndDirections() {
		Pageable pageable = PageRequests.of(2, 50, List.of("createdAt,desc", "lastName"), PATIENTS);

		assertThat(pageable.getPageNumber()).isEqualTo(2);
		assertThat(pageable.getSort()).isEqualTo(
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("lastName"), Sort.Order.asc("id")));
	}

	@Test
	void rejectsFieldsOutsideTheWhitelist() {
		assertThatThrownBy(() -> PageRequests.of(0, 20, List.of("passwordHash,asc"), PATIENTS))
			.isInstanceOf(BadRequestException.class)
			.extracting("code").isEqualTo("INVALID_SORT");
	}

	@Test
	void rejectsUnknownDirections() {
		assertThatThrownBy(() -> PageRequests.of(0, 20, List.of("lastName,sideways"), PATIENTS))
			.isInstanceOf(BadRequestException.class)
			.extracting("code").isEqualTo("INVALID_SORT");
	}

	@Test
	void rejectsNegativePagesAndNonPositiveSizes() {
		assertThatThrownBy(() -> PageRequests.of(-1, 20, null, PATIENTS))
			.extracting("code").isEqualTo("INVALID_PAGE");
		assertThatThrownBy(() -> PageRequests.of(0, 0, null, PATIENTS))
			.extracting("code").isEqualTo("INVALID_PAGE_SIZE");
	}

}
