package com.iatros.backend.kpi;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.kpi.dto.KpiResponse;
import com.iatros.backend.security.AuthenticatedUser;

/** KPIs (api.md §23). {@code from}/{@code to} are clinic-local dates, both inclusive. */
@RestController
@RequestMapping("/api/v1/kpis")
class KpiController {

	private final KpiService service;

	KpiController(KpiService service) {
		this.service = service;
	}

	@GetMapping("/clinic")
	@PreAuthorize("hasRole('OWNER')")
	KpiResponse clinic(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
		return service.clinic(user, from, to);
	}

	@GetMapping("/doctors/{doctorId}")
	@PreAuthorize("hasAnyRole('OWNER', 'DOCTOR')")
	KpiResponse doctor(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID doctorId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
		return service.doctor(user, doctorId, from, to);
	}

}
