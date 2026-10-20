package com.iatros.backend.medicalrecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalConditionRepository extends JpaRepository<MedicalCondition, UUID> {

	List<MedicalCondition> findAllByMedicalRecordIdOrderByCreatedAtDesc(UUID medicalRecordId);

	Optional<MedicalCondition> findByIdAndMedicalRecordId(UUID id, UUID medicalRecordId);

}
