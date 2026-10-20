package com.iatros.backend.doctor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.access.AccessPolicy;
import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.doctor.dto.DoctorResponse;
import com.iatros.backend.doctor.dto.UpdateDoctorProfileRequest;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.persistence.Versions;
import com.iatros.backend.shared.text.Text;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.shared.web.SearchTerms;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.Role;
import com.iatros.backend.user.UserStatus;

/**
 * Doctor profiles (api.md §8, roles-and-permissions.md §6–8). Owners and secretaries see every clinic doctor (booking
 * needs them); doctors see only themselves. Profile edits: owners for any doctor, doctors for themselves.
 */
@Service
public class DoctorService {

	private final DoctorRepository doctors;
	private final AppUserRepository users;
	private final SpecialtyService specialties;
	private final AccessPolicy access;
	private final AuditService audit;

	public DoctorService(DoctorRepository doctors, AppUserRepository users, SpecialtyService specialties,
			AccessPolicy access, AuditService audit) {
		this.doctors = doctors;
		this.users = users;
		this.specialties = specialties;
		this.access = access;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public PageResponse<DoctorResponse> list(AuthenticatedUser caller, String search, String specialtyCode,
			UserStatus status, Pageable pageable) {
		UUID specialtyId = specialtyCode == null || specialtyCode.isBlank() ? null
				: specialties.findActiveByCode(specialtyCode.trim())
					.map(Specialty::getId)
					.orElseThrow(() -> ValidationException.of("specialty", "INVALID"));
		UUID onlyUser = caller.hasRole(Role.DOCTOR) ? caller.userId() : null;
		Page<AppUser> page = users.searchDoctors(caller.clinicId(), onlyUser, specialtyId, status,
				SearchTerms.containsPattern(search), pageable);
		List<UUID> userIds = page.getContent().stream().map(AppUser::getId).toList();
		Map<UUID, Doctor> doctorsByUser = userIds.isEmpty() ? Map.of()
				: doctors.findAllByUserIdIn(userIds).stream()
					.collect(Collectors.toMap(Doctor::getUserId, Function.identity()));
		Map<UUID, String> codes = specialties.codesById();
		return PageResponse.from(page, user -> toResponse(doctorsByUser.get(user.getId()), user, codes));
	}

	@Transactional(readOnly = true)
	public DoctorResponse get(AuthenticatedUser caller, UUID doctorId) {
		return toResponse(access.requireDoctorCalendar(caller, doctorId));
	}

	@Transactional
	public DoctorResponse updateProfile(AuthenticatedUser caller, UUID doctorId, UpdateDoctorProfileRequest request) {
		Doctor doctor = access.requireDoctorManagement(caller, doctorId);
		Versions.require(doctor, request.version());
		Specialty specialty = specialties.findActiveByCode(request.primarySpecialty().trim())
			.orElseThrow(() -> ValidationException.of("primarySpecialty", "INVALID"));
		doctor.updateProfessionalDetails(specialty.getId(), Text.trimToNull(request.additionalSpecialty()),
				Text.trimToNull(request.registrationNumber()));
		doctors.flush();
		audit.record(caller.clinicId(), caller.userId(), AuditActions.DOCTOR_PROFILE_UPDATED,
				AuditActions.ENTITY_DOCTOR, doctor.getId());
		return toResponse(doctor);
	}

	private DoctorResponse toResponse(Doctor doctor) {
		AppUser user = users.findById(doctor.getUserId())
			.orElseThrow(() -> new IllegalStateException("Doctor without user " + doctor.getId()));
		return toResponse(doctor, user, specialties.codesById());
	}

	private static DoctorResponse toResponse(Doctor doctor, AppUser user, Map<UUID, String> specialtyCodes) {
		return new DoctorResponse(doctor.getId(), user.getId(), user.getFirstName(), user.getLastName(),
				user.getEmail(), user.getPhone(), user.getRole(), user.getStatus(),
				specialtyCodes.get(doctor.getPrimarySpecialtyId()), doctor.getAdditionalSpecialty(),
				doctor.getRegistrationNumber(), doctor.getVersion());
	}

}
