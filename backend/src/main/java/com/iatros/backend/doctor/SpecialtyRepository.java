package com.iatros.backend.doctor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialtyRepository extends JpaRepository<Specialty, UUID> {

	Optional<Specialty> findByCode(String code);

	List<Specialty> findAllByActiveTrueOrderByCode();

}
