package com.iatros.backend.doctor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.doctor.dto.DoctorResponse;
import com.iatros.backend.doctor.dto.UpdateDoctorProfileRequest;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.web.PageRequests;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.shared.web.SortWhitelist;
import com.iatros.backend.user.UserStatus;

import jakarta.validation.Valid;

/** Doctor API (api.md §8). Data scope is applied in {@link DoctorService}. */
@RestController
@RequestMapping("/api/v1/doctors")
class DoctorController {

	static final SortWhitelist SORTS = SortWhitelist.of(Sort.by("lastName", "firstName"),
			Map.of("lastName", "lastName", "firstName", "firstName", "status", "status"));

	private final DoctorService doctorService;

	DoctorController(DoctorService doctorService) {
		this.doctorService = doctorService;
	}

	@GetMapping
	PageResponse<DoctorResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String search, @RequestParam(required = false) String specialty,
			@RequestParam(required = false) UserStatus status, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
		return doctorService.list(user, search, specialty, status, PageRequests.of(page, size, sort, SORTS));
	}

	@GetMapping("/{doctorId}")
	DoctorResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID doctorId) {
		return doctorService.get(user, doctorId);
	}

	@PutMapping("/{doctorId}")
	DoctorResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID doctorId,
			@Valid @RequestBody UpdateDoctorProfileRequest request) {
		return doctorService.updateProfile(user, doctorId, request);
	}

}
