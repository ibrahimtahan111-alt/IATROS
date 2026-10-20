package com.iatros.backend.patient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.access.AccessPolicy;
import com.iatros.backend.access.DataScope;
import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.appointment.PatientAppointmentTime;
import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.audit.ChangedFields;
import com.iatros.backend.clinic.ClinicTime;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.medicalrecord.MedicalRecord;
import com.iatros.backend.medicalrecord.MedicalRecordRepository;
import com.iatros.backend.patient.dto.CreatePatientRequest;
import com.iatros.backend.patient.dto.DoctorRef;
import com.iatros.backend.patient.dto.PatientDetailsRequest;
import com.iatros.backend.patient.dto.PatientResponse;
import com.iatros.backend.patient.dto.PatientSummaryResponse;
import com.iatros.backend.patient.dto.UpdatePatientRequest;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.BusinessRuleException;
import com.iatros.backend.shared.error.ForbiddenOperationException;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.persistence.Versions;
import com.iatros.backend.shared.text.Text;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.shared.web.SearchTerms;
import com.iatros.backend.user.Role;

/**
 * Patient identity, contact details, and status (api.md §10, decisions.md D-029, D-036). Every role works within its
 * data scope (AccessPolicy): owners and secretaries on all clinic patients, doctors on the patients they are primary
 * or authorized for. Archiving is owner-only (checked on the controller).
 */
@Service
public class PatientService {

	private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

	private final PatientRepository patients;
	private final PatientDoctorRepository patientDoctors;
	private final PatientDoctorDirectory directory;
	private final PatientDoctorRules doctorRules;
	private final MedicalRecordRepository medicalRecords;
	private final AppointmentRepository appointments;
	private final ClinicTime clinicTime;
	private final AccessPolicy access;
	private final AuditService audit;
	private final Clock clock;

	PatientService(PatientRepository patients, PatientDoctorRepository patientDoctors,
			PatientDoctorDirectory directory, PatientDoctorRules doctorRules, MedicalRecordRepository medicalRecords,
			AppointmentRepository appointments, ClinicTime clinicTime, AccessPolicy access, AuditService audit,
			Clock clock) {
		this.patients = patients;
		this.patientDoctors = patientDoctors;
		this.directory = directory;
		this.doctorRules = doctorRules;
		this.medicalRecords = medicalRecords;
		this.appointments = appointments;
		this.clinicTime = clinicTime;
		this.access = access;
		this.audit = audit;
		this.clock = clock;
	}

	/** {@code status} defaults to ACTIVE: archived patients are hidden from default lists (D-028). */
	@Transactional(readOnly = true)
	public PageResponse<PatientSummaryResponse> list(AuthenticatedUser caller, String search, UUID primaryDoctorId,
			PatientStatus status, Pageable pageable) {
		DataScope scope = access.dataScope(caller);
		Page<Patient> page = patients.search(scope.clinicId(), status == null ? PatientStatus.ACTIVE : status,
				scope.doctorId().orElse(null), primaryDoctorId, SearchTerms.containsPattern(search), pageable);
		List<UUID> ids = page.getContent().stream().map(Patient::getId).toList();
		Map<UUID, List<DoctorRef>> doctors = directory.doctorsOf(ids);
		AppointmentTimes times = appointmentTimes(caller, ids);
		return PageResponse.from(page, patient -> new PatientSummaryResponse(patient.getId(), patient.getFirstName(),
				patient.getLastName(), patient.getDateOfBirth(), patient.getGender(), patient.getPhone(),
				patient.getEmail(), patient.getStatus(),
				PatientDoctorDirectory.primaryOf(doctors.getOrDefault(patient.getId(), List.of())),
				times.next().get(patient.getId()), times.lastVisit().get(patient.getId())));
	}

	@Transactional(readOnly = true)
	public PatientResponse get(AuthenticatedUser caller, UUID patientId) {
		return toResponse(caller, access.requirePatient(caller, patientId));
	}

	/**
	 * Creates the patient, their medical record, and the primary-doctor link in one transaction. Doctors can only
	 * register patients for themselves, so they never create a patient they cannot see.
	 */
	@Transactional
	public PatientResponse create(AuthenticatedUser caller, CreatePatientRequest request) {
		if (caller.hasRole(Role.DOCTOR)
				&& !access.ownDoctorId(caller).map(request.primaryDoctorId()::equals).orElse(false)) {
			throw new ForbiddenOperationException("FORBIDDEN", "Doctors register patients for themselves");
		}
		validateBirthDate(caller, request.dateOfBirth());
		Doctor primary = doctorRules.requireAssignable(caller, request.primaryDoctorId(), "primaryDoctorId");

		Patient patient = new Patient(caller.clinicId(), Text.trim(request.firstName()), Text.trim(request.lastName()),
				request.dateOfBirth(), caller.userId());
		applyDetails(patient, request, caller.userId());
		patients.saveAndFlush(patient);
		medicalRecords.save(new MedicalRecord(caller.clinicId(), patient.getId()));
		patientDoctors.saveAndFlush(new PatientDoctor(caller.clinicId(), patient.getId(), primary.getId(),
				DoctorRelationshipType.PRIMARY, caller.userId()));
		audit.record(caller.clinicId(), caller.userId(), AuditActions.PATIENT_CREATED, AuditActions.ENTITY_PATIENT,
				patient.getId());
		return toResponse(caller, patient);
	}

	@Transactional
	public PatientResponse update(AuthenticatedUser caller, UUID patientId, UpdatePatientRequest request) {
		Patient patient = access.requirePatient(caller, patientId);
		Versions.require(patient, request.version());
		requireActive(patient);
		validateBirthDate(caller, request.dateOfBirth());
		List<String> changedFields = changedFields(patient, request);
		applyDetails(patient, request, caller.userId());
		patients.flush();
		audit.record(caller.clinicId(), caller.userId(), AuditActions.PATIENT_UPDATED, AuditActions.ENTITY_PATIENT,
				patient.getId(), Map.of("changedFields", changedFields));
		return toResponse(caller, patient);
	}

	private static List<String> changedFields(Patient current, PatientDetailsRequest details) {
		return new ChangedFields().compare("firstName", current.getFirstName(), Text.trim(details.firstName()))
			.compare("lastName", current.getLastName(), Text.trim(details.lastName()))
			.compare("dateOfBirth", current.getDateOfBirth(), details.dateOfBirth())
			.compare("gender", current.getGender(), details.gender())
			.compare("phone", current.getPhone(), Text.trimToNull(details.phone()))
			.compare("email", current.getEmail(), Text.trimToNull(details.email()))
			.compare("address", current.getAddress(), Text.trimToNull(details.address()))
			.compare("emergencyContactName", current.getEmergencyContactName(),
					Text.trimToNull(details.emergencyContactName()))
			.compare("emergencyContactPhone", current.getEmergencyContactPhone(),
					Text.trimToNull(details.emergencyContactPhone()))
			.names();
	}

	/** Archives or restores. A patient with upcoming appointments cannot be archived until they are cancelled. */
	@Transactional
	public PatientResponse changeStatus(AuthenticatedUser caller, UUID patientId, PatientStatus requested) {
		Patient patient = access.requirePatient(caller, patientId);
		if (patient.getStatus() == requested) {
			return toResponse(caller, patient);
		}
		Instant now = Instant.now(clock);
		if (requested == PatientStatus.ARCHIVED) {
			if (appointments.existsUpcomingForPatient(patient.getId(), now)) {
				throw new BusinessRuleException("PATIENT_HAS_UPCOMING_APPOINTMENTS",
						"Cancel the patient's upcoming appointments first");
			}
			patient.archive(now, caller.userId());
		}
		else {
			patient.restore(caller.userId());
		}
		patients.flush();
		audit.record(caller.clinicId(), caller.userId(),
				requested == PatientStatus.ARCHIVED ? AuditActions.PATIENT_ARCHIVED : AuditActions.PATIENT_RESTORED,
				AuditActions.ENTITY_PATIENT, patient.getId());
		return toResponse(caller, patient);
	}

	/** Archived patients are read-only, including their medical record (D-036, D-037). */
	public static void requireActive(Patient patient) {
		if (patient.getStatus() == PatientStatus.ARCHIVED) {
			throw new BusinessRuleException("PATIENT_ARCHIVED", "Restore the patient before changing it");
		}
	}

	PatientResponse toResponse(AuthenticatedUser caller, Patient patient) {
		List<DoctorRef> doctors = directory.doctorsOf(patient.getId());
		AppointmentTimes times = appointmentTimes(caller, List.of(patient.getId()));
		return new PatientResponse(patient.getId(), patient.getFirstName(), patient.getLastName(),
				patient.getDateOfBirth(), patient.getGender(), patient.getPhone(), patient.getEmail(),
				patient.getAddress(), patient.getEmergencyContactName(), patient.getEmergencyContactPhone(),
				patient.getStatus(), patient.getArchivedAt(), patient.getCreatedAt(), patient.getVersion(),
				PatientDoctorDirectory.primaryOf(doctors), caller.hasRole(Role.SECRETARY) ? null : doctors,
				times.next().get(patient.getId()), times.lastVisit().get(patient.getId()));
	}

	/** Next appointment and last visit per patient, within the caller's appointment scope (doctors: their own). */
	private AppointmentTimes appointmentTimes(AuthenticatedUser caller, List<UUID> patientIds) {
		if (patientIds.isEmpty()) {
			return new AppointmentTimes(Map.of(), Map.of());
		}
		UUID scopeDoctorId = access.dataScope(caller).doctorId().orElse(null);
		return new AppointmentTimes(
				byPatient(appointments.findNextStarts(caller.clinicId(), patientIds, scopeDoctorId,
						Instant.now(clock))),
				byPatient(appointments.findLastVisits(caller.clinicId(), patientIds, scopeDoctorId)));
	}

	private static Map<UUID, Instant> byPatient(List<PatientAppointmentTime> times) {
		return times.stream()
			.collect(Collectors.toMap(PatientAppointmentTime::patientId, PatientAppointmentTime::startAt));
	}

	private record AppointmentTimes(Map<UUID, Instant> next, Map<UUID, Instant> lastVisit) {
	}

	private void validateBirthDate(AuthenticatedUser caller, LocalDate dateOfBirth) {
		if (dateOfBirth.isBefore(EARLIEST_BIRTH_DATE)) {
			throw ValidationException.of("dateOfBirth", "OUT_OF_RANGE");
		}
		if (dateOfBirth.isAfter(clinicTime.today(caller.clinicId()))) {
			throw ValidationException.of("dateOfBirth", "DATE_IN_FUTURE");
		}
	}

	private static void applyDetails(Patient patient, PatientDetailsRequest details, UUID actorId) {
		patient.updateIdentity(Text.trim(details.firstName()), Text.trim(details.lastName()), details.dateOfBirth(),
				details.gender(), actorId);
		patient.updateContact(Text.trimToNull(details.phone()), Text.trimToNull(details.email()),
				Text.trimToNull(details.address()), Text.trimToNull(details.emergencyContactName()),
				Text.trimToNull(details.emergencyContactPhone()), actorId);
	}

}
