package com.iatros.backend.consultation;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.consultation.dto.ConsultationRequest;
import com.iatros.backend.consultation.dto.ConsultationResponse;
import com.iatros.backend.consultation.dto.ConsultationSummaryResponse;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.web.PageRequests;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.shared.web.SortWhitelist;

import jakarta.validation.Valid;

/** Consultations API (api.md §16). Clinical access and authorship are enforced in {@link ConsultationService}. */
@RestController
@RequestMapping("/api/v1")
class ConsultationController {

	static final SortWhitelist SORTS = SortWhitelist.of(Sort.by(Sort.Direction.DESC, "consultedAt"),
			Map.of("consultedAt", "consultedAt"));

	private final ConsultationService service;

	ConsultationController(ConsultationService service) {
		this.service = service;
	}

	@GetMapping("/patients/{patientId}/consultations")
	PageResponse<ConsultationSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID patientId, @RequestParam(required = false) UUID doctorId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) List<String> sort) {
		return service.list(user, patientId, doctorId, from, to, PageRequests.of(page, size, sort, SORTS));
	}

	@PostMapping("/patients/{patientId}/consultations")
	@ResponseStatus(HttpStatus.CREATED)
	ConsultationResponse create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@Valid @RequestBody ConsultationRequest request) {
		return service.create(user, patientId, request);
	}

	@GetMapping("/consultations/{consultationId}")
	ConsultationResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID consultationId) {
		return service.get(user, consultationId);
	}

	@PutMapping("/consultations/{consultationId}")
	ConsultationResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID consultationId,
			@Valid @RequestBody ConsultationRequest request) {
		return service.update(user, consultationId, request);
	}

}
