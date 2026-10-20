package com.iatros.backend.shared.error;

/** 429. The caller exceeded a rate limit; {@code retryAfterSeconds} is sent as the Retry-After header. */
public class TooManyRequestsException extends ApiException {

	private final long retryAfterSeconds;

	public TooManyRequestsException(long retryAfterSeconds) {
		super("RATE_LIMITED", "Rate limit exceeded");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
