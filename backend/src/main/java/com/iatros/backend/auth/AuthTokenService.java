package com.iatros.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.shared.error.BadRequestException;

/**
 * Issues and consumes single-use tokens for invitations and password resets (decisions.md D-021).
 * The raw token (256 random bits) exists only in the email link; the database stores its SHA-256 hash.
 */
@Service
public class AuthTokenService {

	static final int MAX_TOKEN_LENGTH = 128;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserTokenRepository tokens;
	private final Clock clock;

	public AuthTokenService(UserTokenRepository tokens, Clock clock) {
		this.tokens = tokens;
		this.clock = clock;
	}

	/** Creates a new token and invalidates the user's previous open tokens of the same purpose. */
	@Transactional
	public String issue(UUID userId, TokenPurpose purpose, Duration lifetime) {
		Instant now = Instant.now(clock);
		tokens.findAllByUserIdAndPurposeAndUsedAtIsNull(userId, purpose).forEach(open -> open.markUsed(now));
		byte[] random = new byte[32];
		RANDOM.nextBytes(random);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
		tokens.save(new UserToken(userId, purpose, hash(rawToken), now.plus(lifetime)));
		return rawToken;
	}

	/** Invalidates every open token of the user, e.g. when the account is deactivated. */
	@Transactional
	public void revokeAll(UUID userId) {
		Instant now = Instant.now(clock);
		for (TokenPurpose purpose : TokenPurpose.values()) {
			tokens.findAllByUserIdAndPurposeAndUsedAtIsNull(userId, purpose).forEach(open -> open.markUsed(now));
		}
	}

	/** Marks a valid token as used and returns it; unknown, used, or expired tokens are rejected identically. */
	@Transactional
	public UserToken consume(String rawToken, TokenPurpose purpose) {
		if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_TOKEN_LENGTH) {
			throw invalidToken();
		}
		Instant now = Instant.now(clock);
		UserToken token = tokens.findByTokenHashAndPurpose(hash(rawToken), purpose)
			.filter(candidate -> candidate.isUsable(now))
			.orElseThrow(AuthTokenService::invalidToken);
		token.markUsed(now);
		return token;
	}

	static BadRequestException invalidToken() {
		return new BadRequestException("INVALID_TOKEN", "Token is invalid, used, or expired");
	}

	private static byte[] hash(String rawToken) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

}
