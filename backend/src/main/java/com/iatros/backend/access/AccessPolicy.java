package com.iatros.backend.access;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.appointment.Appointment;
import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.patient.Patient;
import com.iatros.backend.patient.PatientDoctorRepository;
import com.iatros.backend.patient.PatientRepository;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ForbiddenOperationException;
import com.iatros.backend.shared.error.NotFoundException;
import com.iatros.backend.user.Role;

/**
 * Data-scope authorization (roles-and-permissions.md, decisions.md D-018). Services call this before reading or
 * changing clinic data; role-only checks stay in {@code @PreAuthorize}.
 *
 * <ul>
 * <li>Every lookup is restricted to the caller's clinic; IDs from other clinics behave as if they did not exist.</li>
 * <li>A resource outside the caller's scope returns 404 (never 403), so its existence is not revealed.</li>
 * <li>An operation the role may never perform returns 403 {@code FORBIDDEN}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AccessPolicy {

	private final PatientRepository patients;
	private final PatientDoctorRepository patientDoctors;
	private final DoctorRepository doctors;
	private final AppointmentRepository appointments;

	public AccessPolicy(PatientRepository patients, PatientDoctorRepository patientDoctors, DoctorRepository doctors,
			AppointmentRepository appointments) {
		this.patients = patients;
		this.patientDoctors = patientDoctors;
		this.doctors = doctors;
		this.appointments = appointments;
	}

	/** The doctor profile of the caller (doctors, and owners who practise, D-033). */
	public Optional<UUID> ownDoctorId(AuthenticatedUser user) {
		if (user.hasRole(Role.SECRETARY)) {
			return Optional.empty();
		}
		return doctors.findByUserId(user.userId())
			.filter(doctor -> doctor.getClinicId().equals(user.clinicId()))
			.map(Doctor::getId);
	}

	/** Rows the caller may list or search: the whole clinic for owners and secretaries, own data for doctors. */
	public DataScope dataScope(AuthenticatedUser user) {
		if (!user.hasRole(Role.DOCTOR)) {
			return DataScope.clinicWide(user.clinicId());
		}
		return ownDoctorId(user)
			.map(doctorId -> DataScope.doctor(user.clinicId(), doctorId))
			.orElseThrow(AccessPolicy::forbidden);
	}

	/**
	 * Basic (administrative) patient data. Owners and secretaries see every clinic patient; secretaries must only
	 * receive the booking DTO (D-029). Doctors see patients they are primary or additional doctor for.
	 */
	public Patient requirePatient(AuthenticatedUser user, UUID patientId) {
		Patient patient = patients.findByIdAndClinicId(patientId, user.clinicId())
			.orElseThrow(AccessPolicy::patientNotFound);
		if (user.hasRole(Role.DOCTOR) && !isAuthorizedDoctor(user, patientId)) {
			throw patientNotFound();
		}
		return patient;
	}

	/**
	 * Clinical data of a patient: medical record, entries, consultations, documents. Secretaries never have it;
	 * doctors only for authorized patients.
	 */
	public Patient requireClinicalAccess(AuthenticatedUser user, UUID patientId) {
		if (user.hasRole(Role.SECRETARY)) {
			throw forbidden();
		}
		return requirePatient(user, patientId);
	}

	/** An appointment: any in the clinic for owners and secretaries, own appointments for doctors. */
	public Appointment requireAppointment(AuthenticatedUser user, UUID appointmentId) {
		Appointment appointment = appointments.findByIdAndClinicId(appointmentId, user.clinicId())
			.orElseThrow(AccessPolicy::appointmentNotFound);
		if (user.hasRole(Role.DOCTOR) && !ownDoctorId(user).map(appointment.getDoctorId()::equals).orElse(false)) {
			throw appointmentNotFound();
		}
		return appointment;
	}

	/**
	 * A doctor's calendar and schedule for reading: any clinic doctor for owners and secretaries (booking needs it),
	 * only themselves for doctors.
	 */
	public Doctor requireDoctorCalendar(AuthenticatedUser user, UUID doctorId) {
		Doctor doctor = requireDoctorInClinic(user, doctorId);
		if (user.hasRole(Role.DOCTOR)) {
			requireSelf(user, doctor);
		}
		return doctor;
	}

	/**
	 * A doctor's own restricted data: profile edits, schedule changes, KPIs, dashboard. Owners for any clinic doctor,
	 * doctors only for themselves, never secretaries.
	 */
	public Doctor requireDoctorManagement(AuthenticatedUser user, UUID doctorId) {
		if (user.hasRole(Role.SECRETARY)) {
			throw forbidden();
		}
		Doctor doctor = requireDoctorInClinic(user, doctorId);
		if (user.hasRole(Role.DOCTOR)) {
			requireSelf(user, doctor);
		}
		return doctor;
	}

	/**
	 * Booking rule D-027: owners and secretaries book any clinic doctor with any clinic patient; doctors book only
	 * themselves, only with patients they are authorized for. Returns the patient.
	 */
	public Patient requireCanBook(AuthenticatedUser user, UUID doctorId, UUID patientId) {
		Doctor doctor = requireDoctorInClinic(user, doctorId);
		if (user.hasRole(Role.DOCTOR) && !ownDoctorId(user).map(doctor.getId()::equals).orElse(false)) {
			throw forbidden();
		}
		return requirePatient(user, patientId);
	}

	/** The owner, or the doctor the appointment is with: who may start and complete it (D-024). */
	public boolean isClinicalActor(AuthenticatedUser user, Appointment appointment) {
		return user.hasRole(Role.OWNER) || ownDoctorId(user).map(appointment.getDoctorId()::equals).orElse(false);
	}

	private boolean isAuthorizedDoctor(AuthenticatedUser user, UUID patientId) {
		return ownDoctorId(user)
			.map(doctorId -> patientDoctors.existsByPatientIdAndDoctorId(patientId, doctorId))
			.orElse(false);
	}

	private Doctor requireDoctorInClinic(AuthenticatedUser user, UUID doctorId) {
		return doctors.findByIdAndClinicId(doctorId, user.clinicId()).orElseThrow(AccessPolicy::doctorNotFound);
	}

	private void requireSelf(AuthenticatedUser user, Doctor doctor) {
		if (!doctor.getUserId().equals(user.userId())) {
			throw doctorNotFound();
		}
	}

	private static ForbiddenOperationException forbidden() {
		return new ForbiddenOperationException("FORBIDDEN", "Operation not permitted for this role");
	}

	private static NotFoundException patientNotFound() {
		return NotFoundException.of("PATIENT");
	}

	private static NotFoundException appointmentNotFound() {
		return NotFoundException.of("APPOINTMENT");
	}

	private static NotFoundException doctorNotFound() {
		return NotFoundException.of("DOCTOR");
	}

}
