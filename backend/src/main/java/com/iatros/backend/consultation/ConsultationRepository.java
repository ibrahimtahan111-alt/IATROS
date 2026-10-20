package com.iatros.backend.consultation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

	Optional<Consultation> findByIdAndClinicId(UUID id, UUID clinicId);

	Optional<Consultation> findByAppointmentId(UUID appointmentId);

	/** A record's consultations within [from, to), optionally by doctor. Uses consultation_record_time_idx. */
	@Query("""
			select c from Consultation c
			where c.medicalRecordId = :recordId
			  and (:doctorId is null or c.doctorId = :doctorId)
			  and c.consultedAt >= :from
			  and c.consultedAt < :to
			""")
	Page<Consultation> search(@Param("recordId") UUID recordId, @Param("doctorId") UUID doctorId,
			@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

}
