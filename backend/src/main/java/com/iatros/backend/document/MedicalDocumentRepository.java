package com.iatros.backend.document;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicalDocumentRepository extends JpaRepository<MedicalDocument, UUID> {

	Optional<MedicalDocument> findByIdAndClinicId(UUID id, UUID clinicId);

	/**
	 * A record's current (not archived) documents, optionally of one consultation and matching a file-name pattern
	 * (see SearchTerms). Uses document_record_created_idx.
	 */
	@Query("""
			select d from MedicalDocument d
			where d.medicalRecordId = :recordId
			  and d.archivedAt is null
			  and (:consultationId is null or d.consultationId = :consultationId)
			  and (:pattern is null or lower(d.fileName) like :pattern escape '\\')
			""")
	Page<MedicalDocument> search(@Param("recordId") UUID recordId, @Param("consultationId") UUID consultationId,
			@Param("pattern") String pattern, Pageable pageable);

}
