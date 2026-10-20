package com.iatros.backend.auth;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.iatros.backend.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Single-use invitation or password-reset token. Only the SHA-256 hash is stored (decisions.md D-021). */
@Entity
@Table(name = "user_token")
public class UserToken extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30, updatable = false)
	private TokenPurpose purpose;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private byte[] tokenHash;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected UserToken() {
	}

	public UserToken(UUID userId, TokenPurpose purpose, byte[] tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.purpose = purpose;
		this.tokenHash = tokenHash.clone();
		this.expiresAt = expiresAt;
	}

	public boolean isUsable(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	public void markUsed(Instant at) {
		this.usedAt = at;
	}

	public UUID getUserId() {
		return userId;
	}

	public TokenPurpose getPurpose() {
		return purpose;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
