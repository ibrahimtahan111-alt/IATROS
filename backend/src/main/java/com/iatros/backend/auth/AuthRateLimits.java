package com.iatros.backend.auth;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.iatros.backend.security.RateLimiter;

/**
 * Rate limits for authentication endpoints (decisions.md D-016). Per-IP limits slow down broad attacks;
 * per-account limits slow down targeted guessing without permanently locking anyone out.
 */
@Component
class AuthRateLimits {

	private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);
	private static final Duration ONE_HOUR = Duration.ofHours(1);

	private final RateLimiter limiter;

	AuthRateLimits(RateLimiter limiter) {
		this.limiter = limiter;
	}

	void login(String clientIp, String email) {
		limiter.acquire("login:ip:" + clientIp, 30, FIFTEEN_MINUTES);
		limiter.acquire("login:email:" + normalize(email), 10, FIFTEEN_MINUTES);
	}

	void forgotPassword(String clientIp, String email) {
		limiter.acquire("forgot:ip:" + clientIp, 10, FIFTEEN_MINUTES);
		limiter.acquire("forgot:email:" + normalize(email), 5, ONE_HOUR);
	}

	void tokenUse(String clientIp) {
		limiter.acquire("token:ip:" + clientIp, 20, FIFTEEN_MINUTES);
	}

	void changePassword(UUID userId) {
		limiter.acquire("change-password:user:" + userId, 10, FIFTEEN_MINUTES);
	}

	static String normalize(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

}
