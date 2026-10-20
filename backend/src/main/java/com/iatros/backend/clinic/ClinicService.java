package com.iatros.backend.clinic;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.clinic.dto.ClinicResponse;
import com.iatros.backend.clinic.dto.UpdateClinicRequest;
import com.iatros.backend.clinic.dto.UpdateClinicSettingsRequest;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.i18n.Language;
import com.iatros.backend.shared.persistence.Versions;
import com.iatros.backend.shared.text.Text;

/**
 * The caller's clinic profile and operational settings (api.md §6). The clinic is always the authenticated user's
 * clinic; no clinic ID is accepted from the client. Role checks are on the controller.
 */
@Service
public class ClinicService {

	private final ClinicRepository clinics;
	private final ClinicSettingsRepository settingsRepository;
	private final AuditService audit;

	public ClinicService(ClinicRepository clinics, ClinicSettingsRepository settingsRepository, AuditService audit) {
		this.clinics = clinics;
		this.settingsRepository = settingsRepository;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public ClinicResponse get(AuthenticatedUser user) {
		return toResponse(clinic(user.clinicId()), settings(user.clinicId()));
	}

	@Transactional
	public ClinicResponse update(AuthenticatedUser user, UpdateClinicRequest request) {
		Clinic clinic = clinic(user.clinicId());
		Versions.require(clinic, request.version());
		clinic.updateDetails(Text.trim(request.name()), Text.trimToNull(request.email()),
				Text.trimToNull(request.phone()), Text.trimToNull(request.address()));
		audit.record(clinic.getId(), user.userId(), AuditActions.CLINIC_UPDATED, AuditActions.ENTITY_CLINIC,
				clinic.getId());
		clinics.flush();
		return toResponse(clinic, settings(clinic.getId()));
	}

	@Transactional
	public ClinicResponse updateSettings(AuthenticatedUser user, UpdateClinicSettingsRequest request) {
		ClinicSettings settings = settings(user.clinicId());
		Versions.require(settings, request.version());
		settings.update(zone(request.timeZone()), request.defaultAppointmentMinutes(),
				Language.fromCode(request.defaultLanguage()), DayOfWeek.of(request.weekStartDay()));
		audit.record(user.clinicId(), user.userId(), AuditActions.CLINIC_SETTINGS_UPDATED,
				AuditActions.ENTITY_CLINIC, user.clinicId());
		settingsRepository.flush();
		return toResponse(clinic(user.clinicId()), settings);
	}

	/** Only region-based IANA identifiers (e.g. Europe/Paris), so daylight saving time is handled correctly. */
	private static ZoneId zone(String timeZone) {
		String id = timeZone.trim();
		if (!ZoneId.getAvailableZoneIds().contains(id)) {
			throw ValidationException.of("timeZone", "INVALID");
		}
		return ZoneId.of(id);
	}

	private Clinic clinic(UUID clinicId) {
		return clinics.findById(clinicId).orElseThrow(() -> new IllegalStateException("Clinic missing"));
	}

	private ClinicSettings settings(UUID clinicId) {
		return settingsRepository.findById(clinicId)
			.orElseThrow(() -> new IllegalStateException("Clinic settings missing"));
	}

	private static ClinicResponse toResponse(Clinic clinic, ClinicSettings settings) {
		return new ClinicResponse(clinic.getId(), clinic.getName(), clinic.getEmail(), clinic.getPhone(),
				clinic.getAddress(), clinic.getVersion(),
				new ClinicResponse.Settings(settings.getTimeZone().getId(), settings.getDefaultAppointmentMinutes(),
						settings.getDefaultLanguage().code(), settings.getWeekStartDay().getValue(),
						settings.getVersion()));
	}

}
