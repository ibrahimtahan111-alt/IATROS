package com.iatros.backend.patient;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.access.AccessPolicy;
import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.patient.dto.PatientResponse;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.BusinessRuleException;
import com.iatros.backend.shared.error.ConflictException;
import com.iatros.backend.shared.error.NotFoundException;

/**
 * Which doctors may access a patient (roles-and-permissions.md §10, decisions.md D-036). Owner-only (checked on the
 * controller). Links only grant access; changing them never touches the patient's medical history.
 */
@Service
public class PatientDoctorService {

	private final PatientDoctorRepository patientDoctors;
	private final PatientDoctorRules doctorRules;
	private final PatientService patientService;
	private final AccessPolicy access;
	private final AuditService audit;

	PatientDoctorService(PatientDoctorRepository patientDoctors, PatientDoctorRules doctorRules,
			PatientService patientService, AccessPolicy access, AuditService audit) {
		this.patientDoctors = patientDoctors;
		this.doctorRules = doctorRules;
		this.patientService = patientService;
		this.access = access;
		this.audit = audit;
	}

	/**
	 * Makes {@code doctorId} the primary doctor. The previous primary doctor stays authorized, so they keep access to
	 * a patient they have treated; the owner can remove them separately.
	 */
	@Transactional
	public PatientResponse changePrimaryDoctor(AuthenticatedUser caller, UUID patientId, UUID doctorId) {
		Patient patient = access.requirePatient(caller, patientId);
		PatientService.requireActive(patient);
		Doctor doctor = doctorRules.requireAssignable(caller, doctorId, "doctorId");
		PatientDoctor current = patientDoctors
			.findByPatientIdAndRelationshipType(patient.getId(), DoctorRelationshipType.PRIMARY)
			.orElse(null);
		if (current != null && current.getDoctorId().equals(doctor.getId())) {
			return patientService.toResponse(caller, patient);
		}
		if (current != null) {
			current.changeRelationshipType(DoctorRelationshipType.AUTHORIZED);
			// The one-primary index requires the old primary to be demoted before the new one is written.
			patientDoctors.flush();
		}
		patientDoctors.findByPatientIdAndDoctorId(patient.getId(), doctor.getId())
			.ifPresentOrElse(link -> link.changeRelationshipType(DoctorRelationshipType.PRIMARY),
					() -> patientDoctors.save(new PatientDoctor(caller.clinicId(), patient.getId(), doctor.getId(),
							DoctorRelationshipType.PRIMARY, caller.userId())));
		patientDoctors.flush();
		audit.record(caller.clinicId(), caller.userId(), AuditActions.PATIENT_PRIMARY_DOCTOR_CHANGED,
				AuditActions.ENTITY_PATIENT, patient.getId(), Map.of("doctorId", doctor.getId().toString()));
		return patientService.toResponse(caller, patient);
	}

	@Transactional
	public PatientResponse addAuthorizedDoctor(AuthenticatedUser caller, UUID patientId, UUID doctorId) {
		Patient patient = access.requirePatient(caller, patientId);
		PatientService.requireActive(patient);
		Doctor doctor = doctorRules.requireAssignable(caller, doctorId, "doctorId");
		if (patientDoctors.existsByPatientIdAndDoctorId(patient.getId(), doctor.getId())) {
			throw new ConflictException("DOCTOR_ALREADY_AUTHORIZED", "The doctor already has access to the patient");
		}
		patientDoctors.saveAndFlush(new PatientDoctor(caller.clinicId(), patient.getId(), doctor.getId(),
				DoctorRelationshipType.AUTHORIZED, caller.userId()));
		audit.record(caller.clinicId(), caller.userId(), AuditActions.PATIENT_DOCTOR_AUTHORIZED,
				AuditActions.ENTITY_PATIENT, patient.getId(), Map.of("doctorId", doctor.getId().toString()));
		return patientService.toResponse(caller, patient);
	}

	/** The primary doctor cannot be removed: a patient always keeps one (assign another primary doctor first). */
	@Transactional
	public void removeDoctor(AuthenticatedUser caller, UUID patientId, UUID doctorId) {
		Patient patient = access.requirePatient(caller, patientId);
		PatientService.requireActive(patient);
		PatientDoctor link = patientDoctors.findByPatientIdAndDoctorId(patient.getId(), doctorId)
			.orElseThrow(() -> NotFoundException.of("DOCTOR"));
		if (link.getRelationshipType() == DoctorRelationshipType.PRIMARY) {
			throw new BusinessRuleException("PRIMARY_DOCTOR_REQUIRED", "Assign another primary doctor first");
		}
		patientDoctors.delete(link);
		patientDoctors.flush();
		audit.record(caller.clinicId(), caller.userId(), AuditActions.PATIENT_DOCTOR_REMOVED,
				AuditActions.ENTITY_PATIENT, patient.getId(), Map.of("doctorId", doctorId.toString()));
	}

}
