package com.iatros.backend.doctor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.doctor.dto.SpecialtyResponse;

/** Controlled specialty reference data (api.md §9). Codes are stable; names are translated by the frontend. */
@Service
@Transactional(readOnly = true)
public class SpecialtyService {

	private final SpecialtyRepository specialties;

	public SpecialtyService(SpecialtyRepository specialties) {
		this.specialties = specialties;
	}

	public List<SpecialtyResponse> listActive() {
		return specialties.findAllByActiveTrueOrderByCode().stream()
			.map(specialty -> new SpecialtyResponse(specialty.getId(), specialty.getCode()))
			.toList();
	}

	/** An active specialty by code, for assigning to a doctor. */
	public Optional<Specialty> findActiveByCode(String code) {
		return specialties.findByCode(code).filter(Specialty::isActive);
	}

	/** Codes by ID, including inactive specialties that existing doctors may still reference. */
	public Map<UUID, String> codesById() {
		return specialties.findAll().stream().collect(Collectors.toMap(Specialty::getId, Specialty::getCode,
				(first, second) -> first));
	}

}
