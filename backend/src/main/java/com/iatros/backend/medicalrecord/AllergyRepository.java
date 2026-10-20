package com.iatros.backend.medicalrecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AllergyRepository extends JpaRepository<Allergy, UUID> {

	List<Allergy> findAllByMedicalRecordIdOrderByCreatedAtDesc(UUID medicalRecordId);

	Optional<Allergy> findByIdAndMedicalRecordId(UUID id, UUID medicalRecordId);

}
