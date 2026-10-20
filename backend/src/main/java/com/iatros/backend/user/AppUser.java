package com.iatros.backend.user;

import java.time.Instant;
import java.util.UUID;

import com.iatros.backend.shared.i18n.Language;
import com.iatros.backend.shared.persistence.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** An authenticated IATROS account belonging to one clinic. */
@Entity
@Table(name = "app_user")
public class AppUser extends AuditedEntity {

	@Column(name = "clinic_id", nullable = false, updatable = false)
	private UUID clinicId;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(nullable = false, columnDefinition = "citext")
	private String email;

	@Column(length = 40)
	private String phone;

	@Column(name = "password_hash")
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status = UserStatus.INVITED;

	@Column(name = "preferred_language", length = 5)
	private Language preferredLanguage;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "deactivated_at")
	private Instant deactivatedAt;

	protected AppUser() {
	}

	public AppUser(UUID clinicId, String firstName, String lastName, String email, Role role) {
		this.clinicId = clinicId;
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
		this.role = role;
	}

	/** Sets the first password of an invited user and activates the account. */
	public void activate(String passwordHash) {
		if (status != UserStatus.INVITED) {
			throw new IllegalStateException("Only invited users can be activated");
		}
		this.passwordHash = passwordHash;
		this.status = UserStatus.ACTIVE;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public void deactivate(Instant at) {
		this.status = UserStatus.DEACTIVATED;
		this.deactivatedAt = at;
	}

	/** Reactivates a deactivated user; users who never set a password return to INVITED. */
	public void reactivate() {
		this.status = passwordHash == null ? UserStatus.INVITED : UserStatus.ACTIVE;
		this.deactivatedAt = null;
	}

	public void recordLogin(Instant at) {
		this.lastLoginAt = at;
	}

	public void updateProfile(String firstName, String lastName, String phone) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.phone = phone;
	}

	/** The sign-in email can be corrected only before the account is activated (decisions.md D-034). */
	public void changeEmail(String email) {
		if (status != UserStatus.INVITED) {
			throw new IllegalStateException("Only invited users can change email");
		}
		this.email = email;
	}

	public void changePreferredLanguage(Language language) {
		this.preferredLanguage = language;
	}

	public boolean canAuthenticate() {
		return status == UserStatus.ACTIVE && passwordHash != null;
	}

	public UUID getClinicId() {
		return clinicId;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public String getEmail() {
		return email;
	}

	public String getPhone() {
		return phone;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public UserStatus getStatus() {
		return status;
	}

	public Language getPreferredLanguage() {
		return preferredLanguage;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public Instant getDeactivatedAt() {
		return deactivatedAt;
	}

}
