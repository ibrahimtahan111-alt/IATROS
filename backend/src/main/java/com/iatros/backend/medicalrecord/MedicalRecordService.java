package com.iatros.backend.medicalrecord;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.clinic.ClinicTime;
import com.iatros.backend.medicalrecord.dto.AllergyRequest;
import com.iatros.backend.medicalrecord.dto.AllergyResponse;
import com.iatros.backend.medicalrecord.dto.ConditionRequest;
import com.iatros.backend.medicalrecord.dto.ConditionResponse;
import com.iatros.backend.medicalrecord.dto.HistoryEntryRequest;
import com.iatros.backend.medicalrecord.dto.HistoryEntryResponse;
import com.iatros.backend.medicalrecord.dto.MedicalRecordResponse;
import com.iatros.backend.medicalrecord.dto.MedicationRequest;
import com.iatros.backend.medicalrecord.dto.MedicationResponse;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.NotFoundException;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.text.Text;

/**
 * Structured sections of the medical record: allergies, conditions, medications, and past history (api.md §12–15,
 * decisions.md D-037). Any clinician with access to the patient maintains them; removal is a soft delete (D-030).
 */
@Service
public class MedicalRecordService {

	private static final Comparator<String> TEXT = String.CASE_INSENSITIVE_ORDER;

	private final MedicalRecordAccess recordAccess;
	private final AllergyRepository allergies;
	private final MedicalConditionRepository conditions;
	private final MedicationRepository medications;
	private final MedicalHistoryEntryRepository history;
	private final ClinicalEntryWriter writer;
	private final ClinicTime clinicTime;

	MedicalRecordService(MedicalRecordAccess recordAccess, AllergyRepository allergies,
			MedicalConditionRepository conditions, MedicationRepository medications,
			MedicalHistoryEntryRepository history, ClinicalEntryWriter writer, ClinicTime clinicTime) {
		this.recordAccess = recordAccess;
		this.allergies = allergies;
		this.conditions = conditions;
		this.medications = medications;
		this.history = history;
		this.writer = writer;
		this.clinicTime = clinicTime;
	}

	/** Ordered for reading: allergies by name, active conditions and medications first, most recent history first. */
	@Transactional(readOnly = true)
	public MedicalRecordResponse get(AuthenticatedUser caller, UUID patientId) {
		MedicalRecord record = recordAccess.readable(caller, patientId);
		UUID recordId = record.getId();
		return new MedicalRecordResponse(record.getPatientId(),
				allergies.findAllByMedicalRecordIdOrderByCreatedAtDesc(recordId).stream()
					.sorted(Comparator.comparing(Allergy::getAllergen, TEXT))
					.map(MedicalRecordService::toResponse)
					.toList(),
				conditions.findAllByMedicalRecordIdOrderByCreatedAtDesc(recordId).stream()
					.sorted(Comparator.comparing(MedicalCondition::getStatus)
						.thenComparing(MedicalCondition::getName, TEXT))
					.map(MedicalRecordService::toResponse)
					.toList(),
				medications.findAllByMedicalRecordIdOrderByCreatedAtDesc(recordId).stream()
					.sorted(Comparator.comparing((Medication medication) -> !medication.isActive())
						.thenComparing(Medication::getName, TEXT))
					.map(MedicalRecordService::toResponse)
					.toList(),
				history.findAllByMedicalRecordIdOrderByCreatedAtDesc(recordId).stream()
					.sorted(Comparator.comparing(MedicalHistoryEntry::getOccurredOn,
							Comparator.nullsLast(Comparator.reverseOrder())))
					.map(MedicalRecordService::toResponse)
					.toList());
	}

	// Allergies

	@Transactional
	public AllergyResponse addAllergy(AuthenticatedUser caller, UUID patientId, AllergyRequest request) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		Allergy allergy = new Allergy(record.getClinicId(), record.getId(), Text.trim(request.allergen()),
				caller.userId());
		apply(allergy, request, caller);
		return toResponse(writer.create(caller, record, allergy, allergies, AuditActions.ENTITY_ALLERGY));
	}

	@Transactional
	public AllergyResponse updateAllergy(AuthenticatedUser caller, UUID patientId, UUID allergyId,
			AllergyRequest request) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		Allergy allergy = allergies.findByIdAndMedicalRecordId(allergyId, record.getId())
			.orElseThrow(() -> NotFoundException.of("ALLERGY"));
		return toResponse(writer.update(caller, record, allergy, request.version(),
				entry -> apply(entry, request, caller), allergies, AuditActions.ENTITY_ALLERGY));
	}

	@Transactional
	public void removeAllergy(AuthenticatedUser caller, UUID patientId, UUID allergyId) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		Allergy allergy = allergies.findByIdAndMedicalRecordId(allergyId, record.getId())
			.orElseThrow(() -> NotFoundException.of("ALLERGY"));
		writer.delete(caller, record, allergy, allergies, AuditActions.ENTITY_ALLERGY);
	}

	// Conditions

	@Transactional
	public ConditionResponse addCondition(AuthenticatedUser caller, UUID patientId, ConditionRequest request) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		requireNotFuture(caller, request.diagnosedOn(), "diagnosedOn");
		MedicalCondition condition = new MedicalCondition(record.getClinicId(), record.getId(),
				Text.trim(request.name()), caller.userId());
		apply(condition, request, caller);
		return toResponse(writer.create(caller, record, condition, conditions, AuditActions.ENTITY_MEDICAL_CONDITION));
	}

	@Transactional
	public ConditionResponse updateCondition(AuthenticatedUser caller, UUID patientId, UUID conditionId,
			ConditionRequest request) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		MedicalCondition condition = conditions.findByIdAndMedicalRecordId(conditionId, record.getId())
			.orElseThrow(() -> NotFoundException.of("MEDICAL_CONDITION"));
		requireNotFuture(caller, request.diagnosedOn(), "diagnosedOn");
		return toResponse(writer.update(caller, record, condition, request.version(),
				entry -> apply(entry, request, caller), conditions, AuditActions.ENTITY_MEDICAL_CONDITION));
	}

	@Transactional
	public void removeCondition(AuthenticatedUser caller, UUID patientId, UUID conditionId) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		MedicalCondition condition = conditions.findByIdAndMedicalRecordId(conditionId, record.getId())
			.orElseThrow(() -> NotFoundException.of("MEDICAL_CONDITION"));
		writer.delete(caller, record, condition, conditions, AuditActions.ENTITY_MEDICAL_CONDITION);
	}

	// Medications

	@Transactional
	public MedicationResponse addMedication(AuthenticatedUser caller, UUID patientId, MedicationRequest request) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		requireOrdered(request);
		Medication medication = new Medication(record.getClinicId(), record.getId(), Text.trim(request.name()),
				caller.userId());
		apply(medication, request, caller);
		return toResponse(writer.create(caller, record, medication, medications, AuditActions.ENTITY_MEDICATION));
	}

	@Transactional
	public MedicationResponse updateMedication(AuthenticatedUser caller, UUID patientId, UUID medicationId,
			MedicationRequest request) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		Medication medication = medications.findByIdAndMedicalRecordId(medicationId, record.getId())
			.orElseThrow(() -> NotFoundException.of("MEDICATION"));
		requireOrdered(request);
		return toResponse(writer.update(caller, record, medication, request.version(),
				entry -> apply(entry, request, caller), medications, AuditActions.ENTITY_MEDICATION));
	}

	@Transactional
	public void removeMedication(AuthenticatedUser caller, UUID patientId, UUID medicationId) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		Medication medication = medications.findByIdAndMedicalRecordId(medicationId, record.getId())
			.orElseThrow(() -> NotFoundException.of("MEDICATION"));
		writer.delete(caller, record, medication, medications, AuditActions.ENTITY_MEDICATION);
	}

	// Past medical history

	@Transactional
	public HistoryEntryResponse addHistoryEntry(AuthenticatedUser caller, UUID patientId,
			HistoryEntryRequest request) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		requireNotFuture(caller, request.occurredOn(), "occurredOn");
		MedicalHistoryEntry entry = new MedicalHistoryEntry(record.getClinicId(), record.getId(),
				Text.trim(request.description()), caller.userId());
		apply(entry, request, caller);
		return toResponse(writer.create(caller, record, entry, history, AuditActions.ENTITY_MEDICAL_HISTORY_ENTRY));
	}

	@Transactional
	public HistoryEntryResponse updateHistoryEntry(AuthenticatedUser caller, UUID patientId, UUID entryId,
			HistoryEntryRequest request) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		MedicalHistoryEntry entry = history.findByIdAndMedicalRecordId(entryId, record.getId())
			.orElseThrow(() -> NotFoundException.of("MEDICAL_HISTORY_ENTRY"));
		requireNotFuture(caller, request.occurredOn(), "occurredOn");
		return toResponse(writer.update(caller, record, entry, request.version(),
				found -> apply(found, request, caller), history, AuditActions.ENTITY_MEDICAL_HISTORY_ENTRY));
	}

	@Transactional
	public void removeHistoryEntry(AuthenticatedUser caller, UUID patientId, UUID entryId) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		MedicalHistoryEntry entry = history.findByIdAndMedicalRecordId(entryId, record.getId())
			.orElseThrow(() -> NotFoundException.of("MEDICAL_HISTORY_ENTRY"));
		writer.delete(caller, record, entry, history, AuditActions.ENTITY_MEDICAL_HISTORY_ENTRY);
	}

	private void requireNotFuture(AuthenticatedUser caller, LocalDate date, String field) {
		if (date != null && date.isAfter(clinicTime.today(caller.clinicId()))) {
			throw ValidationException.of(field, "DATE_IN_FUTURE");
		}
	}

	private static void requireOrdered(MedicationRequest request) {
		if (request.startDate() != null && request.endDate() != null
				&& request.endDate().isBefore(request.startDate())) {
			throw ValidationException.of("endDate", "END_BEFORE_START");
		}
	}

	private static void apply(Allergy allergy, AllergyRequest request, AuthenticatedUser caller) {
		allergy.update(Text.trim(request.allergen()), Text.trimToNull(request.reaction()), request.severity(),
				Text.trimToNull(request.notes()), caller.userId());
	}

	private static void apply(MedicalCondition condition, ConditionRequest request, AuthenticatedUser caller) {
		condition.update(Text.trim(request.name()), Text.trimToNull(request.description()), request.status(),
				request.diagnosedOn(), caller.userId());
	}

	private static void apply(Medication medication, MedicationRequest request, AuthenticatedUser caller) {
		medication.update(Text.trim(request.name()), Text.trimToNull(request.dosage()),
				Text.trimToNull(request.frequency()), Text.trimToNull(request.notes()), request.startDate(),
				request.endDate(), request.active(), caller.userId());
	}

	private static void apply(MedicalHistoryEntry entry, HistoryEntryRequest request, AuthenticatedUser caller) {
		entry.update(Text.trim(request.description()), request.occurredOn(), Text.trimToNull(request.notes()),
				caller.userId());
	}

	private static AllergyResponse toResponse(Allergy allergy) {
		return new AllergyResponse(allergy.getId(), allergy.getAllergen(), allergy.getReaction(),
				allergy.getSeverity(), allergy.getNotes(), allergy.getUpdatedAt(), allergy.getVersion());
	}

	private static ConditionResponse toResponse(MedicalCondition condition) {
		return new ConditionResponse(condition.getId(), condition.getName(), condition.getDescription(),
				condition.getStatus(), condition.getDiagnosedOn(), condition.getUpdatedAt(), condition.getVersion());
	}

	private static MedicationResponse toResponse(Medication medication) {
		return new MedicationResponse(medication.getId(), medication.getName(), medication.getDosage(),
				medication.getFrequency(), medication.getNotes(), medication.getStartDate(), medication.getEndDate(),
				medication.isActive(), medication.getUpdatedAt(), medication.getVersion());
	}

	private static HistoryEntryResponse toResponse(MedicalHistoryEntry entry) {
		return new HistoryEntryResponse(entry.getId(), entry.getDescription(), entry.getOccurredOn(),
				entry.getNotes(), entry.getUpdatedAt(), entry.getVersion());
	}

}
