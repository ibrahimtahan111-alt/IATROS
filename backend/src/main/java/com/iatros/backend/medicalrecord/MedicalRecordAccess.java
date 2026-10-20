package com.iatros.backend.medicalrecord;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.access.AccessPolicy;
import com.iatros.backend.patient.Patient;
import com.iatros.backend.patient.PatientService;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.NotFoundException;

/**
 * Resolves a patient's medical record for clinical work (decisions.md D-018, D-037): owners for any clinic patient,
 * doctors for patients they are linked to, never secretaries. Archived patients' records are read-only.
 */
@Component
@Transactional(readOnly = true)
public class MedicalRecordAccess {

	private final AccessPolicy access;
	private final MedicalRecordRepository records;

	MedicalRecordAccess(AccessPolicy access, MedicalRecordRepository records) {
		this.access = access;
		this.records = records;
	}

	public MedicalRecord readable(AuthenticatedUser caller, UUID patientId) {
		return recordOf(caller, access.requireClinicalAccess(caller, patientId));
	}

	public MedicalRecord writable(AuthenticatedUser caller, UUID patientId) {
		Patient patient = access.requireClinicalAccess(caller, patientId);
		PatientService.requireActive(patient);
		return recordOf(caller, patient);
	}

	/** For entities reached by their own id (consultations): the record must belong to a patient the caller may see. */
	public Patient patientOf(AuthenticatedUser caller, UUID medicalRecordId) {
		MedicalRecord record = records.findById(medicalRecordId)
			.filter(found -> found.getClinicId().equals(caller.clinicId()))
			.orElseThrow(() -> NotFoundException.of("PATIENT"));
		return access.requireClinicalAccess(caller, record.getPatientId());
	}

	private MedicalRecord recordOf(AuthenticatedUser caller, Patient patient) {
		return records.findByPatientIdAndClinicId(patient.getId(), caller.clinicId())
			.orElseThrow(() -> new IllegalStateException("Medical record missing for patient " + patient.getId()));
	}

}
