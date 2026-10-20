package com.iatros.backend.provisioning;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.auth.InvitationService;
import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.clinic.ClinicSettings;
import com.iatros.backend.clinic.ClinicSettingsRepository;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.doctor.Specialty;
import com.iatros.backend.doctor.SpecialtyService;
import com.iatros.backend.shared.i18n.Language;
import com.iatros.backend.shared.text.Text;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.Role;

/**
 * Creates a clinic, its settings, and its owner, then emails the owner an activation link (decisions.md D-020).
 * Operator-only: it is reachable from the command line, never from the HTTP API.
 */
@Service
public class ClinicProvisioningService {

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final ClinicRepository clinics;
	private final ClinicSettingsRepository clinicSettings;
	private final AppUserRepository users;
	private final DoctorRepository doctors;
	private final SpecialtyService specialties;
	private final InvitationService invitations;
	private final AuditService audit;

	public ClinicProvisioningService(ClinicRepository clinics, ClinicSettingsRepository clinicSettings,
			AppUserRepository users, DoctorRepository doctors, SpecialtyService specialties,
			InvitationService invitations, AuditService audit) {
		this.clinics = clinics;
		this.clinicSettings = clinicSettings;
		this.users = users;
		this.doctors = doctors;
		this.specialties = specialties;
		this.invitations = invitations;
		this.audit = audit;
	}

	/** Returns the new clinic's ID. Invalid input is rejected with every problem listed, before anything is saved. */
	@Transactional
	public UUID provision(ClinicProvisioningRequest request) {
		List<String> problems = new ArrayList<>();
		String clinicName = required(request.clinicName(), "clinic name", 200, problems);
		String firstName = required(request.ownerFirstName(), "owner first name", 100, problems);
		String lastName = required(request.ownerLastName(), "owner last name", 100, problems);
		String email = required(request.ownerEmail(), "owner email", 254, problems);
		if (email != null && !EMAIL.matcher(email).matches()) {
			problems.add("owner email is not a valid address");
		}
		String zone = Text.trimToNull(request.timeZone());
		if (zone == null || !ZoneId.getAvailableZoneIds().contains(zone)) {
			problems.add("time zone must be an IANA region identifier, e.g. Europe/Paris");
		}
		String languageCode = Text.trimToNull(request.language()) == null ? "en" : request.language().trim();
		if (!languageCode.equals("en") && !languageCode.equals("fr")) {
			problems.add("language must be en or fr");
		}
		Specialty specialty = null;
		String specialtyCode = Text.trimToNull(request.ownerSpecialty());
		if (specialtyCode != null) {
			specialty = specialties.findActiveByCode(specialtyCode).orElse(null);
			if (specialty == null) {
				problems.add("owner specialty is not an active specialty code");
			}
		}
		if (email != null && users.existsByEmail(email)) {
			problems.add("owner email is already used by another account");
		}
		if (!problems.isEmpty()) {
			throw new IllegalArgumentException("Cannot provision clinic: " + String.join("; ", problems));
		}

		Clinic clinic = clinics.save(new Clinic(clinicName));
		ClinicSettings settings = new ClinicSettings(clinic.getId(), ZoneId.of(zone));
		settings.update(ZoneId.of(zone), ClinicSettings.DEFAULT_APPOINTMENT_MINUTES, Language.fromCode(languageCode),
				DayOfWeek.MONDAY);
		clinicSettings.save(settings);
		AppUser owner = users.saveAndFlush(new AppUser(clinic.getId(), firstName, lastName, email, Role.OWNER));
		if (specialty != null) {
			doctors.saveAndFlush(new Doctor(clinic.getId(), owner.getId(), specialty.getId()));
		}
		audit.record(clinic.getId(), null, AuditActions.CLINIC_PROVISIONED, AuditActions.ENTITY_CLINIC,
				clinic.getId(), Map.of("ownerIsDoctor", specialty != null));
		invitations.sendInvitation(owner, null);
		return clinic.getId();
	}

	private static String required(String value, String label, int maxLength, List<String> problems) {
		String trimmed = Text.trimToNull(value);
		if (trimmed == null) {
			problems.add(label + " is required");
			return null;
		}
		if (trimmed.length() > maxLength) {
			problems.add(label + " must be at most " + maxLength + " characters");
		}
		return trimmed;
	}

}
