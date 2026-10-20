package com.iatros.backend.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.iatros.backend.user.Role;

/**
 * The authenticated principal stored in the server-side session. It holds only identifiers and the role,
 * never personal or medical data. The username is the user ID (stable even if the email changes), which is
 * also the principal name Spring Session indexes to find and revoke a user's sessions.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID userId;
	private final UUID clinicId;
	private final Role role;
	private final boolean enabled;
	private String passwordHash;

	public AuthenticatedUser(UUID userId, UUID clinicId, Role role, String passwordHash, boolean enabled) {
		this.userId = userId;
		this.clinicId = clinicId;
		this.role = role;
		this.passwordHash = passwordHash;
		this.enabled = enabled;
	}

	public UUID userId() {
		return userId;
	}

	/** The clinic of the authenticated user: the only clinic whose data this request may touch. */
	public UUID clinicId() {
		return clinicId;
	}

	public Role role() {
		return role;
	}

	public boolean hasRole(Role candidate) {
		return role == candidate;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return userId.toString();
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}

	@Override
	public String toString() {
		return "AuthenticatedUser[userId=" + userId + ", role=" + role + "]";
	}

}
