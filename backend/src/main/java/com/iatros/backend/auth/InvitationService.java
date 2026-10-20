package com.iatros.backend.auth;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.shared.error.BusinessRuleException;
import com.iatros.backend.shared.mail.EmailSender;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.UserStatus;

/**
 * Sends (or re-sends) the activation link to an invited team member (decisions.md D-021). Callers are
 * responsible for authorizing the action (only the owner manages staff) and for clinic scoping.
 */
@Service
public class InvitationService {

	static final Duration INVITATION_LIFETIME = Duration.ofHours(72);

	private final AuthTokenService tokens;
	private final AuthEmails emails;
	private final EmailSender emailSender;
	private final ClinicRepository clinics;
	private final AuditService audit;

	public InvitationService(AuthTokenService tokens, AuthEmails emails, EmailSender emailSender,
			ClinicRepository clinics, AuditService audit) {
		this.tokens = tokens;
		this.emails = emails;
		this.emailSender = emailSender;
		this.clinics = clinics;
		this.audit = audit;
	}

	@Transactional
	public void sendInvitation(AppUser user, UUID invitedBy) {
		if (user.getStatus() != UserStatus.INVITED) {
			throw new BusinessRuleException("USER_NOT_INVITED", "Only invited users can receive an invitation");
		}
		String clinicName = clinics.findById(user.getClinicId()).orElseThrow().getName();
		String rawToken = tokens.issue(user.getId(), TokenPurpose.INVITATION, INVITATION_LIFETIME);
		emailSender.send(emails.invitation(user, clinicName, rawToken));
		audit.record(user.getClinicId(), invitedBy, AuditActions.INVITATION_SENT, AuditActions.ENTITY_USER,
				user.getId());
	}

}
