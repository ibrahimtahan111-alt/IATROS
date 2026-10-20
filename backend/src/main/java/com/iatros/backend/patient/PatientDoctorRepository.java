package com.iatros.backend.patient;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientDoctorRepository extends JpaRepository<PatientDoctor, UUID> {

	boolean existsByPatientIdAndDoctorId(UUID patientId, UUID doctorId);

	List<PatientDoctor> findAllByPatientId(UUID patientId);

	List<PatientDoctor> findAllByPatientIdIn(Collection<UUID> patientIds);

	List<PatientDoctor> findAllByDoctorIdAndPatientIdIn(UUID doctorId, Collection<UUID> patientIds);

	Optional<PatientDoctor> findByPatientIdAndDoctorId(UUID patientId, UUID doctorId);

	Optional<PatientDoctor> findByPatientIdAndRelationshipType(UUID patientId, DoctorRelationshipType relationshipType);

	/** Active patients the doctor is primary or additional doctor for. */
	@Query("""
			select count(pd) from PatientDoctor pd, Patient p
			where p.id = pd.patientId
			  and pd.doctorId = :doctorId
			  and p.status = com.iatros.backend.patient.PatientStatus.ACTIVE
			""")
	long countActivePatients(@Param("doctorId") UUID doctorId);

	/** Active patients the doctor is primary doctor for. */
	@Query("""
			select count(pd) from PatientDoctor pd, Patient p
			where p.id = pd.patientId
			  and pd.doctorId = :doctorId
			  and pd.relationshipType = com.iatros.backend.patient.DoctorRelationshipType.PRIMARY
			  and p.status = com.iatros.backend.patient.PatientStatus.ACTIVE
			""")
	long countActivePrimaryPatients(@Param("doctorId") UUID doctorId);

}
