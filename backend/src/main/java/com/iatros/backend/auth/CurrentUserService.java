package com.iatros.backend.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.auth.dto.CurrentUserResponse;
import com.iatros.backend.auth.dto.UpdatePreferencesRequest;
import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.clinic.ClinicSettingsRepository;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.AuthenticationFailedException;
import com.iatros.backend.shared.i18n.Language;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;

@Service
public class CurrentUserService {

	private final AppUserRepository users;
	private final ClinicRepository clinics;
	private final ClinicSettingsRepository clinicSettings;
	private final DoctorRepository doctors;
	private final AuditService audit;
	private final Clock clock;

	public CurrentUserService(AppUserRepository users, ClinicRepository clinics,
			ClinicSettingsRepository clinicSettings, DoctorRepository doctors, AuditService audit, Clock clock) {
		this.users = users;
		this.clinics = clinics;
		this.clinicSettings = clinicSettings;
		this.doctors = doctors;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public CurrentUserResponse describe(AuthenticatedUser principal) {
		return toResponse(loadUser(principal));
	}

	@Transactional
	public CurrentUserResponse recordLogin(AuthenticatedUser principal) {
		AppUser user = loadUser(principal);
		user.recordLogin(Instant.now(clock));
		audit.record(user.getClinicId(), user.getId(), AuditActions.LOGIN_SUCCEEDED, AuditActions.ENTITY_USER,
				user.getId());
		return toResponse(user);
	}

	@Transactional
	public CurrentUserResponse updatePreferences(AuthenticatedUser principal, UpdatePreferencesRequest request) {
		AppUser user = loadUser(principal);
		user.changePreferredLanguage(
				request.preferredLanguage() == null ? null : Language.fromCode(request.preferredLanguage()));
		return toResponse(user);
	}

	private AppUser loadUser(AuthenticatedUser principal) {
		return users.findByIdAndClinicId(principal.userId(), principal.clinicId())
			.orElseThrow(() -> new AuthenticationFailedException("UNAUTHENTICATED", "Session user no longer exists"));
	}

	private CurrentUserResponse toResponse(AppUser user) {
		Clinic clinic = clinics.findById(user.getClinicId()).orElseThrow();
		var settings = clinicSettings.findById(clinic.getId());
		var clinicSummary = new CurrentUserResponse.ClinicSummary(clinic.getId(), clinic.getName(),
				settings.map(s -> s.getTimeZone().getId()).orElse(null),
				settings.map(s -> s.getDefaultLanguage().code()).orElse(null));
		return new CurrentUserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
				user.getRole(), user.getPreferredLanguage() == null ? null : user.getPreferredLanguage().code(),
				doctors.findByUserId(user.getId()).map(Doctor::getId).orElse(null), clinicSummary);
	}

}
