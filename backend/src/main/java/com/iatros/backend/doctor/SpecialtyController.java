package com.iatros.backend.doctor;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.doctor.dto.SpecialtyResponse;

/** Specialty API (api.md §9). Reference data, readable by every role; not editable through the API in V1. */
@RestController
@RequestMapping("/api/v1/specialties")
class SpecialtyController {

	private final SpecialtyService specialtyService;

	SpecialtyController(SpecialtyService specialtyService) {
		this.specialtyService = specialtyService;
	}

	@GetMapping
	List<SpecialtyResponse> list() {
		return specialtyService.listActive();
	}

}
