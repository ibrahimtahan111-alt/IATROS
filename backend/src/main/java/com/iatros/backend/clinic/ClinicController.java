package com.iatros.backend.clinic;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.clinic.dto.ClinicResponse;
import com.iatros.backend.clinic.dto.UpdateClinicRequest;
import com.iatros.backend.clinic.dto.UpdateClinicSettingsRequest;
import com.iatros.backend.security.AuthenticatedUser;

import jakarta.validation.Valid;

/** Clinic API (api.md §6). Every role can read its clinic (calendars need the settings); only the owner edits. */
@RestController
@RequestMapping("/api/v1/clinic")
class ClinicController {

	private final ClinicService clinicService;

	ClinicController(ClinicService clinicService) {
		this.clinicService = clinicService;
	}

	@GetMapping
	ClinicResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
		return clinicService.get(user);
	}

	@PutMapping
	@PreAuthorize("hasRole('OWNER')")
	ClinicResponse update(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody UpdateClinicRequest request) {
		return clinicService.update(user, request);
	}

	@PutMapping("/settings")
	@PreAuthorize("hasRole('OWNER')")
	ClinicResponse updateSettings(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody UpdateClinicSettingsRequest request) {
		return clinicService.updateSettings(user, request);
	}

}
