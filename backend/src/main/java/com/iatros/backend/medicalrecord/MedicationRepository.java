package com.iatros.backend.medicalrecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {

	List<Medication> findAllByMedicalRecordIdOrderByCreatedAtDesc(UUID medicalRecordId);

	Optional<Medication> findByIdAndMedicalRecordId(UUID id, UUID medicalRecordId);

}
