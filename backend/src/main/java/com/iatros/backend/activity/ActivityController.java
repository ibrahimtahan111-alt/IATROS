package com.iatros.backend.activity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.activity.dto.ActivityItemResponse;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.web.PageRequests;
import com.iatros.backend.shared.web.PageResponse;

/** Clinic activity (api.md §24). Always newest first; the data scope is enforced in the service. */
@RestController
@RequestMapping("/api/v1/activity")
class ActivityController {

	private final ActivityService service;

	ActivityController(ActivityService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('OWNER', 'DOCTOR')")
	PageResponse<ActivityItemResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) List<ActivityCategory> type,
			@RequestParam(required = false) UUID doctorId, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return service.list(user, from, to, type, doctorId, PageRequests.unsorted(page, size));
	}

}
