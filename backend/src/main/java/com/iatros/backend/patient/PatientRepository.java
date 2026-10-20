package com.iatros.backend.patient;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

	Optional<Patient> findByIdAndClinicId(UUID id, UUID clinicId);

	List<Patient> findAllByClinicIdAndIdIn(UUID clinicId, Collection<UUID> ids);

	long countByClinicIdAndStatus(UUID clinicId, PatientStatus status);

	/**
	 * Clinic patients matching optional filters (api.md §10). {@code scopeDoctorId} limits the result to patients the
	 * doctor is primary or authorized for (a doctor's data scope); {@code primaryDoctorId} is the user's filter.
	 * {@code pattern} is a SearchTerms LIKE pattern matched case-insensitively against the generated
	 * {@code search_text} (name, phone, email), which has a trigram index.
	 */
	@Query("""
			select p from Patient p
			where p.clinicId = :clinicId
			  and p.status = :status
			  and (:scopeDoctorId is null or exists (
			        select 1 from PatientDoctor pd where pd.patientId = p.id and pd.doctorId = :scopeDoctorId))
			  and (:primaryDoctorId is null or exists (
			        select 1 from PatientDoctor pp
			        where pp.patientId = p.id and pp.doctorId = :primaryDoctorId
			          and pp.relationshipType = com.iatros.backend.patient.DoctorRelationshipType.PRIMARY))
			  and (:pattern is null or p.searchText ilike :pattern escape '\\')
			""")
	Page<Patient> search(@Param("clinicId") UUID clinicId, @Param("status") PatientStatus status,
			@Param("scopeDoctorId") UUID scopeDoctorId, @Param("primaryDoctorId") UUID primaryDoctorId,
			@Param("pattern") String pattern, Pageable pageable);

}
