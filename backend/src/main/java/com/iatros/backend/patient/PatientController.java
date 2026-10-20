package com.iatros.backend.patient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.patient.dto.CreatePatientRequest;
import com.iatros.backend.patient.dto.PatientDoctorRequest;
import com.iatros.backend.patient.dto.PatientResponse;
import com.iatros.backend.patient.dto.PatientSummaryResponse;
import com.iatros.backend.patient.dto.UpdatePatientRequest;
import com.iatros.backend.patient.dto.UpdatePatientStatusRequest;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.web.PageRequests;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.shared.web.SortWhitelist;

import jakarta.validation.Valid;

/**
 * Patient API (api.md §10). Reading, creating, and editing are open to every role within its data scope (enforced in
 * the services); archiving and doctor links are owner-only (decisions.md D-036).
 */
@RestController
@RequestMapping("/api/v1/patients")
class PatientController {

	static final SortWhitelist SORTS = SortWhitelist.of(Sort.by("lastName", "firstName"),
			Map.of("lastName", "lastName", "firstName", "firstName", "dateOfBirth", "dateOfBirth", "createdAt",
					"createdAt"));

	private final PatientService patientService;
	private final PatientDoctorService patientDoctorService;

	PatientController(PatientService patientService, PatientDoctorService patientDoctorService) {
		this.patientService = patientService;
		this.patientDoctorService = patientDoctorService;
	}

	@GetMapping
	PageResponse<PatientSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String search, @RequestParam(required = false) UUID primaryDoctorId,
			@RequestParam(required = false) PatientStatus status, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
		return patientService.list(user, search, primaryDoctorId, status, PageRequests.of(page, size, sort, SORTS));
	}

	@GetMapping("/{id}")
	PatientResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return patientService.get(user, id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PatientResponse create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody CreatePatientRequest request) {
		return patientService.create(user, request);
	}

	@PutMapping("/{id}")
	PatientResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdatePatientRequest request) {
		return patientService.update(user, id, request);
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasRole('OWNER')")
	PatientResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdatePatientStatusRequest request) {
		return patientService.changeStatus(user, id, request.status());
	}

	@PatchMapping("/{id}/primary-doctor")
	@PreAuthorize("hasRole('OWNER')")
	PatientResponse changePrimaryDoctor(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
			@Valid @RequestBody PatientDoctorRequest request) {
		return patientDoctorService.changePrimaryDoctor(user, id, request.doctorId());
	}

	@PostMapping("/{id}/doctors")
	@PreAuthorize("hasRole('OWNER')")
	PatientResponse addAuthorizedDoctor(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
			@Valid @RequestBody PatientDoctorRequest request) {
		return patientDoctorService.addAuthorizedDoctor(user, id, request.doctorId());
	}

	@DeleteMapping("/{id}/doctors/{doctorId}")
	@PreAuthorize("hasRole('OWNER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeDoctor(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
			@PathVariable UUID doctorId) {
		patientDoctorService.removeDoctor(user, id, doctorId);
	}

}
