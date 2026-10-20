package com.iatros.backend.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.dashboard.dto.DoctorDashboardResponse;
import com.iatros.backend.dashboard.dto.OwnerDashboardResponse;
import com.iatros.backend.security.AuthenticatedUser;

/** Role dashboards (api.md §22). Each returns the caller's own dashboard; there is no way to request another's. */
@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController {

	private final DashboardService service;

	DashboardController(DashboardService service) {
		this.service = service;
	}

	@GetMapping("/owner")
	@PreAuthorize("hasRole('OWNER')")
	OwnerDashboardResponse owner(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.owner(user);
	}

	/** Doctors, and owners who practise (D-033). */
	@GetMapping("/doctor")
	@PreAuthorize("hasAnyRole('OWNER', 'DOCTOR')")
	DoctorDashboardResponse doctor(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.doctor(user);
	}

}
