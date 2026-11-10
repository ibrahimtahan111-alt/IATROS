package com.iatros.backend.auth;

import java.time.Duration;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.auth.dto.ChangePasswordRequest;
import com.iatros.backend.auth.dto.TokenPasswordRequest;
import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.clinic.ClinicStatus;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.security.UserSessionService;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.mail.EmailSender;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.UserStatus;

/** Password change, forgot/reset password, and invitation acceptance (api.md §5, security.md §7). */
@Service
public class PasswordService {

	static final Duration RESET_TOKEN_LIFETIME = Duration.ofHours(1);

	private final AppUserRepository users;
	private final ClinicRepository clinics;
	private final PasswordEncoder passwordEncoder;
	private final AuthTokenService tokens;
	private final AuthEmails emails;
	private final EmailSender emailSender;
	private final UserSessionService sessions;
	private final AuditService audit;
	private final AuthRateLimits rateLimits;

	public PasswordService(AppUserRepository users, ClinicRepository clinics, PasswordEncoder passwordEncoder,
			AuthTokenService tokens, AuthEmails emails, EmailSender emailSender, UserSessionService sessions,
			AuditService audit, AuthRateLimits rateLimits) {
		this.users = users;
		this.clinics = clinics;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.emails = emails;
		this.emailSender = emailSender;
		this.sessions = sessions;
		this.audit = audit;
		this.rateLimits = rateLimits;
	}

	/** Changes the caller's password and signs out their other sessions. */
	@Transactional
	public void changePassword(AuthenticatedUser principal, ChangePasswordRequest request, String currentSessionId) {
		rateLimits.changePassword(principal.userId());
		requireMatchingConfirmation(request.newPassword(), request.confirmPassword());
		AppUser user = users.findByIdAndClinicId(principal.userId(), principal.clinicId()).orElseThrow();
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw ValidationException.of("currentPassword", "INCORRECT_PASSWORD");
		}
		user.changePassword(passwordEncoder.encode(request.newPassword()));
		sessions.invalidateAllExcept(user.getId(), currentSessionId);
		audit.record(user.getClinicId(), user.getId(), AuditActions.PASSWORD_CHANGED, AuditActions.ENTITY_USER,
				user.getId());
	}

	/**
	 * Sends a reset link if the email belongs to an active account. The caller always receives the same response,
	 * so the endpoint does not reveal which emails exist.
	 */
	@Transactional
	public void requestPasswordReset(String email, String clientIp) {
		String normalized = AuthRateLimits.normalize(email);
		rateLimits.forgotPassword(clientIp, normalized);
		users.findByEmail(normalized)
			.filter(AppUser::canAuthenticate)
			.filter(this::clinicIsActive)
			.ifPresent(user -> issueResetLink(user, user.getId()));
	}

	/**
	 * Sends a one-hour reset link to an active account. Used when the clinic owner starts the reset; the member
	 * still chooses the new password, and completing it signs them out everywhere.
	 */
	@Transactional
	public void issueResetLink(AppUser user, UUID actorId) {
		String rawToken = tokens.issue(user.getId(), TokenPurpose.PASSWORD_RESET, RESET_TOKEN_LIFETIME);
		emailSender.send(emails.passwordReset(user, rawToken));
		audit.record(user.getClinicId(), actorId, AuditActions.PASSWORD_RESET_REQUESTED, AuditActions.ENTITY_USER,
				user.getId());
	}

	/** Sets a new password from a reset token and signs the user out everywhere. */
	@Transactional
	public void resetPassword(TokenPasswordRequest request, String clientIp) {
		rateLimits.tokenUse(clientIp);
		requireMatchingConfirmation(request.newPassword(), request.confirmPassword());
		UserToken token = tokens.consume(request.token(), TokenPurpose.PASSWORD_RESET);
		AppUser user = users.findById(token.getUserId())
			.filter(AppUser::canAuthenticate)
			.orElseThrow(AuthTokenService::invalidToken);
		user.changePassword(passwordEncoder.encode(request.newPassword()));
		sessions.invalidateAll(user.getId());
		audit.record(user.getClinicId(), user.getId(), AuditActions.PASSWORD_RESET, AuditActions.ENTITY_USER,
				user.getId());
	}

	/** Sets the first password of an invited team member and activates the account (decisions.md D-021). */
	@Transactional
	public void acceptInvitation(TokenPasswordRequest request, String clientIp) {
		rateLimits.tokenUse(clientIp);
		requireMatchingConfirmation(request.newPassword(), request.confirmPassword());
		UserToken token = tokens.consume(request.token(), TokenPurpose.INVITATION);
		AppUser user = users.findById(token.getUserId())
			.filter(candidate -> candidate.getStatus() == UserStatus.INVITED)
			.orElseThrow(AuthTokenService::invalidToken);
		user.activate(passwordEncoder.encode(request.newPassword()));
		audit.record(user.getClinicId(), user.getId(), AuditActions.INVITATION_ACCEPTED, AuditActions.ENTITY_USER,
				user.getId());
	}

	private boolean clinicIsActive(AppUser user) {
		return clinics.findById(user.getClinicId()).map(c -> c.getStatus() == ClinicStatus.ACTIVE).orElse(false);
	}

	private static void requireMatchingConfirmation(String newPassword, String confirmPassword) {
		if (!newPassword.equals(confirmPassword)) {
			throw ValidationException.of("confirmPassword", "PASSWORD_MISMATCH");
		}
	}

}
