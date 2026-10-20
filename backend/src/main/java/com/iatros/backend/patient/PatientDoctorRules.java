package com.iatros.backend.patient;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.UserStatus;

/** Which doctors may be linked to a patient: a doctor of the caller's clinic whose account is not deactivated. */
@Component
@Transactional(readOnly = true)
class PatientDoctorRules {

	private final DoctorRepository doctors;
	private final AppUserRepository users;

	PatientDoctorRules(DoctorRepository doctors, AppUserRepository users) {
		this.doctors = doctors;
		this.users = users;
	}

	/** Reported as a field error on {@code field}, since the doctor comes from the request body. */
	Doctor requireAssignable(AuthenticatedUser caller, UUID doctorId, String field) {
		Doctor doctor = doctors.findByIdAndClinicId(doctorId, caller.clinicId())
			.orElseThrow(() -> ValidationException.of(field, "INVALID"));
		boolean deactivated = users.findById(doctor.getUserId())
			.map(user -> user.getStatus() == UserStatus.DEACTIVATED)
			.orElse(true);
		if (deactivated) {
			throw ValidationException.of(field, "DOCTOR_DEACTIVATED");
		}
		return doctor;
	}

}
