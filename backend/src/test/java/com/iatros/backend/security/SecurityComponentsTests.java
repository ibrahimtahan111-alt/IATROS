package com.iatros.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.iatros.backend.shared.error.TooManyRequestsException;

class SecurityComponentsTests {

	private static final Instant NOW = Instant.parse("2027-04-12T09:00:00Z");

	@Test
	void passwordsAreHashedWithArgon2() {
		PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

		String hash = encoder.encode("correct-horse-battery");

		assertThat(hash).startsWith("{argon2}");
		assertThat(encoder.matches("correct-horse-battery", hash)).isTrue();
		assertThat(encoder.matches("wrong-horse-battery", hash)).isFalse();
	}

	@Test
	void rateLimiterBlocksAfterTheLimitUntilTheWindowEnds() {
		MutableClock clock = new MutableClock(NOW);
		RateLimiter limiter = new RateLimiter(clock);

		for (int i = 0; i < 3; i++) {
			limiter.acquire("key", 3, Duration.ofMinutes(1));
		}
		assertThatThrownBy(() -> limiter.acquire("key", 3, Duration.ofMinutes(1)))
			.isInstanceOf(TooManyRequestsException.class)
			.extracting("retryAfterSeconds").isEqualTo(60L);
		assertThatCode(() -> limiter.acquire("other-key", 3, Duration.ofMinutes(1))).doesNotThrowAnyException();

		clock.set(NOW.plus(Duration.ofMinutes(1)));
		assertThatCode(() -> limiter.acquire("key", 3, Duration.ofMinutes(1))).doesNotThrowAnyException();
	}

	@Test
	void sessionsOlderThanTheAbsoluteLifetimeAreInvalidated() throws Exception {
		var filter = new AbsoluteSessionTimeoutFilter(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(12));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AbsoluteSessionTimeoutFilter.AUTHENTICATED_AT, NOW.minus(Duration.ofHours(13)));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setSession(session);

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void sessionsWithinTheAbsoluteLifetimeAreKept() throws Exception {
		var filter = new AbsoluteSessionTimeoutFilter(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(12));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AbsoluteSessionTimeoutFilter.AUTHENTICATED_AT, NOW.minus(Duration.ofHours(11)));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setSession(session);

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(session.isInvalid()).isFalse();
	}

	private static final class MutableClock extends Clock {

		private final AtomicReference<Instant> now;

		MutableClock(Instant start) {
			this.now = new AtomicReference<>(start);
		}

		void set(Instant instant) {
			now.set(instant);
		}

		@Override
		public Instant instant() {
			return now.get();
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

	}

}
