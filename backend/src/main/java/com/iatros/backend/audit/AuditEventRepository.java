package com.iatros.backend.audit;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Append-only: exposes no update or delete operations. */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

	AuditEvent save(AuditEvent event);

	Page<AuditEvent> findAllByClinicId(UUID clinicId, Pageable pageable);

	/** The clinic's events with the given actions in [from, to), newest first (api.md §24). */
	@Query(value = """
			select * from audit_event e
			where e.clinic_id = :clinicId
			  and e.action in (:actions)
			  and e.occurred_at >= :from
			  and e.occurred_at < :to
			order by e.occurred_at desc, e.id desc
			""", countQuery = """
			select count(*) from audit_event e
			where e.clinic_id = :clinicId
			  and e.action in (:actions)
			  and e.occurred_at >= :from
			  and e.occurred_at < :to
			""", nativeQuery = true)
	Page<AuditEvent> findClinicActivity(@Param("clinicId") UUID clinicId,
			@Param("actions") Collection<String> actions, @Param("from") Instant from, @Param("to") Instant to,
			Pageable pageable);

	/**
	 * A doctor's activity (decisions.md D-039): what the doctor did, and what others did to the doctor's
	 * appointments, schedule, patient access, or profile.
	 */
	@Query(value = """
			select * from audit_event e
			where e.clinic_id = :clinicId
			  and e.action in (:actions)
			  and e.occurred_at >= :from
			  and e.occurred_at < :to
			  and (e.actor_user_id = :userId
			       or e.metadata ->> 'doctorId' = :doctorRef
			       or (e.entity_type = 'DOCTOR' and e.entity_id = :doctorId))
			order by e.occurred_at desc, e.id desc
			""", countQuery = """
			select count(*) from audit_event e
			where e.clinic_id = :clinicId
			  and e.action in (:actions)
			  and e.occurred_at >= :from
			  and e.occurred_at < :to
			  and (e.actor_user_id = :userId
			       or e.metadata ->> 'doctorId' = :doctorRef
			       or (e.entity_type = 'DOCTOR' and e.entity_id = :doctorId))
			""", nativeQuery = true)
	Page<AuditEvent> findDoctorActivity(@Param("clinicId") UUID clinicId, @Param("userId") UUID userId,
			@Param("doctorId") UUID doctorId, @Param("doctorRef") String doctorRef,
			@Param("actions") Collection<String> actions, @Param("from") Instant from, @Param("to") Instant to,
			Pageable pageable);

}
