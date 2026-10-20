package com.iatros.backend.medicalrecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalHistoryEntryRepository extends JpaRepository<MedicalHistoryEntry, UUID> {

	List<MedicalHistoryEntry> findAllByMedicalRecordIdOrderByCreatedAtDesc(UUID medicalRecordId);

	Optional<MedicalHistoryEntry> findByIdAndMedicalRecordId(UUID id, UUID medicalRecordId);

}
