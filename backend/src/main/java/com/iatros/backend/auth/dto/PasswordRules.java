package com.iatros.backend.auth.dto;

/**
 * Password policy (security.md §5): length only, no composition rules, following NIST SP 800-63B.
 * The maximum bounds hashing cost.
 */
public final class PasswordRules {

	public static final int MIN_LENGTH = 8;
	public static final int MAX_LENGTH = 128;

	private PasswordRules() {
	}

}
