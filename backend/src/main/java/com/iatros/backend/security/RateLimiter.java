package com.iatros.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.iatros.backend.shared.error.TooManyRequestsException;

/**
 * In-memory fixed-window rate limiter for public authentication endpoints (decisions.md D-016).
 * Suitable for a single backend instance; with several instances this moves to the reverse proxy or a shared store.
 * Keys must not contain secrets; callers pass a purpose prefix plus a client IP or normalized email.
 */
@Component
public class RateLimiter {

	private static final int MAX_TRACKED_KEYS = 50_000;

	private final Clock clock;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	public RateLimiter(Clock clock) {
		this.clock = clock;
	}

	/** Counts one attempt for {@code key}; throws 429 when more than {@code limit} attempts fall in the window. */
	public void acquire(String key, int limit, Duration window) {
		Instant now = Instant.now(clock);
		if (windows.size() > MAX_TRACKED_KEYS) {
			windows.values().removeIf(existing -> existing.isExpired(now));
		}
		Window current = windows.compute(key, (k, existing) ->
				existing == null || existing.isExpired(now) ? new Window(now.plus(window), 1) : existing.increment());
		if (current.count() > limit) {
			long retryAfter = Math.max(1, Duration.between(now, current.resetsAt()).toSeconds());
			throw new TooManyRequestsException(retryAfter);
		}
	}

	/** Clears all counters. Used by tests. */
	public void reset() {
		windows.clear();
	}

	private record Window(Instant resetsAt, int count) {

		boolean isExpired(Instant now) {
			return !now.isBefore(resetsAt);
		}

		Window increment() {
			return new Window(resetsAt, count + 1);
		}

	}

}
