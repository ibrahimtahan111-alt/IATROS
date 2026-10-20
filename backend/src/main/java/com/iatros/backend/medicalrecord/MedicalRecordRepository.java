package com.iatros.backend.medicalrecord;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

	Optional<MedicalRecord> findByPatientIdAndClinicId(UUID patientId, UUID clinicId);

}
