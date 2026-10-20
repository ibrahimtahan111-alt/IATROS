package com.iatros.backend.medicalrecord;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalNoteRepository extends JpaRepository<MedicalNote, UUID> {

	Page<MedicalNote> findAllByMedicalRecordId(UUID medicalRecordId, Pageable pageable);

	Optional<MedicalNote> findByIdAndMedicalRecordId(UUID id, UUID medicalRecordId);

}
