package com.iatros.backend.medicalrecord;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.persistence.Versions;

/**
 * The save, version check, soft delete, and audit steps shared by every medical-record section. Audit metadata holds
 * identifiers only, never clinical content (security.md §23).
 */
@Component
class ClinicalEntryWriter {

	private final AuditService audit;
	private final Clock clock;

	ClinicalEntryWriter(AuditService audit, Clock clock) {
		this.audit = audit;
		this.clock = clock;
	}

	<E extends ClinicalEntry> E create(AuthenticatedUser caller, MedicalRecord record, E entry,
			JpaRepository<E, UUID> repository, String entityType) {
		repository.saveAndFlush(entry);
		record(caller, record, AuditActions.MEDICAL_ENTRY_CREATED, entityType, entry.getId());
		return entry;
	}

	<E extends ClinicalEntry> E update(AuthenticatedUser caller, MedicalRecord record, E entry, Long version,
			Consumer<E> changes, JpaRepository<E, UUID> repository, String entityType) {
		if (version == null) {
			throw ValidationException.of("version", "REQUIRED");
		}
		Versions.require(entry, version);
		changes.accept(entry);
		repository.flush();
		record(caller, record, AuditActions.MEDICAL_ENTRY_UPDATED, entityType, entry.getId());
		return entry;
	}

	<E extends ClinicalEntry> void delete(AuthenticatedUser caller, MedicalRecord record, E entry,
			JpaRepository<E, UUID> repository, String entityType) {
		entry.softDelete(Instant.now(clock), caller.userId());
		repository.flush();
		record(caller, record, AuditActions.MEDICAL_ENTRY_DELETED, entityType, entry.getId());
	}

	private void record(AuthenticatedUser caller, MedicalRecord record, String action, String entityType,
			UUID entityId) {
		audit.record(caller.clinicId(), caller.userId(), action, entityType, entityId,
				Map.of("patientId", record.getPatientId().toString()));
	}

}
