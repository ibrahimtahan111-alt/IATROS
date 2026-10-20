package com.iatros.backend.staff;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.audit.ChangedFields;
import com.iatros.backend.auth.AuthTokenService;
import com.iatros.backend.auth.InvitationService;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.doctor.Specialty;
import com.iatros.backend.doctor.SpecialtyService;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.security.RateLimiter;
import com.iatros.backend.security.UserSessionService;
import com.iatros.backend.shared.error.BusinessRuleException;
import com.iatros.backend.shared.error.ConflictException;
import com.iatros.backend.shared.error.NotFoundException;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.persistence.Versions;
import com.iatros.backend.shared.text.Text;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.shared.web.SearchTerms;
import com.iatros.backend.staff.dto.CreateStaffRequest;
import com.iatros.backend.staff.dto.DoctorProfileRequest;
import com.iatros.backend.staff.dto.StaffMemberResponse;
import com.iatros.backend.staff.dto.UpdateStaffRequest;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.Role;
import com.iatros.backend.user.UserStatus;

/**
 * Staff management (api.md §7, decisions.md D-021, D-022, D-034). Only the owner calls these operations (checked on
 * the controller); every lookup is scoped to the caller's clinic, so other clinics' members are simply not found.
 */
@Service
public class StaffService {

	private static final Duration INVITATION_RESEND_WINDOW = Duration.ofHours(1);
	private static final int INVITATION_RESENDS_PER_WINDOW = 5;

	private final AppUserRepository users;
	private final DoctorRepository doctors;
	private final SpecialtyService specialties;
	private final AppointmentRepository appointments;
	private final InvitationService invitations;
	private final AuthTokenService tokens;
	private final UserSessionService sessions;
	private final RateLimiter rateLimiter;
	private final AuditService audit;
	private final Clock clock;

	public StaffService(AppUserRepository users, DoctorRepository doctors, SpecialtyService specialties,
			AppointmentRepository appointments, InvitationService invitations, AuthTokenService tokens,
			UserSessionService sessions, RateLimiter rateLimiter, AuditService audit, Clock clock) {
		this.users = users;
		this.doctors = doctors;
		this.specialties = specialties;
		this.appointments = appointments;
		this.invitations = invitations;
		this.tokens = tokens;
		this.sessions = sessions;
		this.rateLimiter = rateLimiter;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<StaffMemberResponse> list(AuthenticatedUser caller, String search, Role role,
			UserStatus status, Pageable pageable) {
		Page<AppUser> page = users.search(caller.clinicId(), role, status, SearchTerms.containsPattern(search),
				pageable);
		List<UUID> userIds = page.getContent().stream().map(AppUser::getId).toList();
		Map<UUID, Doctor> doctorsByUser = userIds.isEmpty() ? Map.of()
				: doctors.findAllByUserIdIn(userIds).stream()
					.collect(Collectors.toMap(Doctor::getUserId, Function.identity()));
		Map<UUID, String> specialtyCodes = specialties.codesById();
		return PageResponse.from(page, user -> toResponse(user, doctorsByUser.get(user.getId()), specialtyCodes));
	}

	@Transactional(readOnly = true)
	public StaffMemberResponse get(AuthenticatedUser caller, UUID id) {
		return toResponse(member(caller, id));
	}

	/** Creates a DOCTOR or SECRETARY in the caller's clinic and emails the invitation (D-021, D-022). */
	@Transactional
	public StaffMemberResponse create(AuthenticatedUser caller, CreateStaffRequest request) {
		if (request.role() == Role.OWNER) {
			throw ValidationException.of("role", "INVALID");
		}
		Specialty specialty = request.role() == Role.DOCTOR ? requireDoctorProfile(request.doctor()) : null;
		if (request.role() != Role.DOCTOR && request.doctor() != null) {
			throw ValidationException.of("doctor", "INVALID");
		}
		String email = Text.trim(request.email());
		if (users.existsByEmail(email)) {
			throw new ConflictException("EMAIL_ALREADY_USED", "Email already used");
		}

		AppUser user = new AppUser(caller.clinicId(), Text.trim(request.firstName()), Text.trim(request.lastName()),
				email, request.role());
		user.updateProfile(user.getFirstName(), user.getLastName(), Text.trimToNull(request.phone()));
		users.saveAndFlush(user);
		Doctor doctor = null;
		if (specialty != null) {
			DoctorProfileRequest profile = request.doctor();
			doctor = new Doctor(caller.clinicId(), user.getId(), specialty.getId());
			doctor.updateProfessionalDetails(specialty.getId(), Text.trimToNull(profile.additionalSpecialty()),
					Text.trimToNull(profile.registrationNumber()));
			doctors.saveAndFlush(doctor);
		}
		audit.record(caller.clinicId(), caller.userId(), AuditActions.STAFF_CREATED, AuditActions.ENTITY_USER,
				user.getId(), Map.of("role", user.getRole().name()));
		// Constraint violations surface on the flushes above, before any email leaves the system.
		invitations.sendInvitation(user, caller.userId());
		return toResponse(user, doctor, specialties.codesById());
	}

	/** Updates account details. Correcting an invited member's email re-sends the invitation to the new address. */
	@Transactional
	public StaffMemberResponse update(AuthenticatedUser caller, UUID id, UpdateStaffRequest request) {
		AppUser user = member(caller, id);
		Versions.require(user, request.version());
		String email = Text.trim(request.email());
		boolean emailChanged = !email.equalsIgnoreCase(user.getEmail());
		List<String> changedFields = new ChangedFields()
			.compare("firstName", user.getFirstName(), Text.trim(request.firstName()))
			.compare("lastName", user.getLastName(), Text.trim(request.lastName()))
			.compare("phone", user.getPhone(), Text.trimToNull(request.phone()))
			.compare("email", emailChanged, false)
			.names();
		if (emailChanged) {
			if (user.getStatus() != UserStatus.INVITED) {
				throw new BusinessRuleException("EMAIL_CHANGE_NOT_ALLOWED",
						"Email can change only before the account is activated");
			}
			if (users.existsByEmail(email)) {
				throw new ConflictException("EMAIL_ALREADY_USED", "Email already used");
			}
			user.changeEmail(email);
		}
		user.updateProfile(Text.trim(request.firstName()), Text.trim(request.lastName()),
				Text.trimToNull(request.phone()));
		users.flush();
		audit.record(caller.clinicId(), caller.userId(), AuditActions.STAFF_UPDATED, AuditActions.ENTITY_USER,
				user.getId(), Map.of("changedFields", changedFields));
		if (emailChanged) {
			invitations.sendInvitation(user, caller.userId());
		}
		return toResponse(user);
	}

	/**
	 * Deactivates or reactivates a member. Deactivation signs the member out everywhere and revokes open links; it
	 * is refused for the owner and for doctors who still have upcoming appointments (D-034).
	 */
	@Transactional
	public StaffMemberResponse changeStatus(AuthenticatedUser caller, UUID id, UserStatus requested) {
		AppUser user = member(caller, id);
		if (requested == UserStatus.INVITED) {
			throw ValidationException.of("status", "INVALID");
		}
		if (user.getRole() == Role.OWNER) {
			throw new BusinessRuleException("OWNER_STATUS_LOCKED", "The clinic owner cannot be deactivated");
		}
		boolean deactivate = requested == UserStatus.DEACTIVATED;
		boolean alreadyDeactivated = user.getStatus() == UserStatus.DEACTIVATED;
		if (deactivate == alreadyDeactivated) {
			return toResponse(user);
		}

		Instant now = Instant.now(clock);
		if (deactivate) {
			doctors.findByUserId(user.getId())
				.filter(doctor -> appointments.existsUpcomingForDoctor(doctor.getId(), now))
				.ifPresent(doctor -> {
					throw new BusinessRuleException("STAFF_HAS_UPCOMING_APPOINTMENTS",
							"Cancel or reassign upcoming appointments first");
				});
			user.deactivate(now);
			tokens.revokeAll(user.getId());
			sessions.invalidateAll(user.getId());
		}
		else {
			user.reactivate();
		}
		users.flush();
		audit.record(caller.clinicId(), caller.userId(),
				deactivate ? AuditActions.STAFF_DEACTIVATED : AuditActions.STAFF_REACTIVATED,
				AuditActions.ENTITY_USER, user.getId());
		return toResponse(user);
	}

	/** Issues a fresh invitation link (the previous one stops working). Only for members who are still INVITED. */
	@Transactional
	public void resendInvitation(AuthenticatedUser caller, UUID id) {
		AppUser user = member(caller, id);
		rateLimiter.acquire("invitation:user:" + user.getId(), INVITATION_RESENDS_PER_WINDOW,
				INVITATION_RESEND_WINDOW);
		invitations.sendInvitation(user, caller.userId());
	}

	private Specialty requireDoctorProfile(DoctorProfileRequest profile) {
		if (profile == null) {
			throw ValidationException.of("doctor", "REQUIRED");
		}
		return specialties.findActiveByCode(profile.primarySpecialty().trim())
			.orElseThrow(() -> ValidationException.of("doctor.primarySpecialty", "INVALID"));
	}

	private AppUser member(AuthenticatedUser caller, UUID id) {
		return users.findByIdAndClinicId(id, caller.clinicId()).orElseThrow(() -> NotFoundException.of("STAFF"));
	}

	private StaffMemberResponse toResponse(AppUser user) {
		return toResponse(user, doctors.findByUserId(user.getId()).orElse(null), specialties.codesById());
	}

	private static StaffMemberResponse toResponse(AppUser user, Doctor doctor, Map<UUID, String> specialtyCodes) {
		StaffMemberResponse.DoctorProfile profile = doctor == null ? null
				: new StaffMemberResponse.DoctorProfile(doctor.getId(), specialtyCodes.get(doctor.getPrimarySpecialtyId()),
						doctor.getAdditionalSpecialty(), doctor.getRegistrationNumber());
		return new StaffMemberResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
				user.getPhone(), user.getRole(), user.getStatus(), user.getLastLoginAt(), user.getCreatedAt(),
				user.getVersion(), profile);
	}

}
