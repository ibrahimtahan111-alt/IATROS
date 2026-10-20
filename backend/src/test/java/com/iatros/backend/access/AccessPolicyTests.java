package com.iatros.backend.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.appointment.Appointment;
import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.appointment.AppointmentType;
import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.doctor.SpecialtyRepository;
import com.iatros.backend.patient.DoctorRelationshipType;
import com.iatros.backend.patient.Patient;
import com.iatros.backend.patient.PatientDoctor;
import com.iatros.backend.patient.PatientDoctorRepository;
import com.iatros.backend.patient.PatientRepository;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ApiException;
import com.iatros.backend.shared.error.ForbiddenOperationException;
import com.iatros.backend.shared.error.NotFoundException;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.Role;

/**
 * Phase 3 definition of done (roadmap.md §7): doctors cannot reach other doctors' data, secretaries cannot reach
 * medical records, and nobody can reach another clinic's data.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccessPolicyTests {

	@Autowired private AccessPolicy access;
	@Autowired private ClinicRepository clinics;
	@Autowired private AppUserRepository users;
	@Autowired private DoctorRepository doctors;
	@Autowired private SpecialtyRepository specialties;
	@Autowired private PatientRepository patients;
	@Autowired private PatientDoctorRepository patientDoctors;
	@Autowired private AppointmentRepository appointments;

	private AuthenticatedUser owner;
	private AuthenticatedUser doctorA;
	private AuthenticatedUser doctorB;
	private AuthenticatedUser secretary;

	private Doctor ownerDoctor;
	private Doctor doctorAProfile;
	private Doctor doctorBProfile;
	private Patient patientOfA;
	private Patient patientOfB;
	private Appointment appointmentOfA;
	private Appointment appointmentOfB;

	private Doctor otherClinicDoctor;
	private Patient otherClinicPatient;
	private Appointment otherClinicAppointment;

	@BeforeEach
	void setUp() {
		Clinic clinic = clinics.save(new Clinic("Access Clinic"));
		owner = principal(clinic, Role.OWNER);
		ownerDoctor = doctorProfile(clinic, owner);
		doctorA = principal(clinic, Role.DOCTOR);
		doctorAProfile = doctorProfile(clinic, doctorA);
		doctorB = principal(clinic, Role.DOCTOR);
		doctorBProfile = doctorProfile(clinic, doctorB);
		secretary = principal(clinic, Role.SECRETARY);

		patientOfA = patient(clinic, doctorAProfile, owner);
		patientOfB = patient(clinic, doctorBProfile, owner);
		appointmentOfA = appointment(clinic, patientOfA, doctorAProfile, owner);
		appointmentOfB = appointment(clinic, patientOfB, doctorBProfile, owner);

		Clinic otherClinic = clinics.save(new Clinic("Other Clinic"));
		AuthenticatedUser otherOwner = principal(otherClinic, Role.OWNER);
		otherClinicDoctor = doctorProfile(otherClinic, otherOwner);
		otherClinicPatient = patient(otherClinic, otherClinicDoctor, otherOwner);
		otherClinicAppointment = appointment(otherClinic, otherClinicPatient, otherClinicDoctor, otherOwner);
	}

	@Nested
	class Owner {

		@Test
		void reachesEveryPatientAndClinicalRecordInTheClinic() {
			assertThat(access.requirePatient(owner, patientOfA.getId())).isEqualTo(patientOfA);
			assertThat(access.requireClinicalAccess(owner, patientOfB.getId())).isEqualTo(patientOfB);
		}

		@Test
		void managesEveryDoctorAndAppointment() {
			assertThat(access.requireDoctorManagement(owner, doctorBProfile.getId())).isEqualTo(doctorBProfile);
			assertThat(access.requireAppointment(owner, appointmentOfB.getId())).isEqualTo(appointmentOfB);
			access.requireCanBook(owner, doctorAProfile.getId(), patientOfB.getId());
		}

		@Test
		void hasClinicWideScopeAndKeepsTheirOwnDoctorProfile() {
			assertThat(access.dataScope(owner).isClinicWide()).isTrue();
			assertThat(access.ownDoctorId(owner)).contains(ownerDoctor.getId());
		}

	}

	@Nested
	class DoctorRole {

		@Test
		void reachesOwnPatientsOnly() {
			assertThat(access.requirePatient(doctorA, patientOfA.getId())).isEqualTo(patientOfA);
			assertThat(access.requireClinicalAccess(doctorA, patientOfA.getId())).isEqualTo(patientOfA);

			expectNotFound(() -> access.requirePatient(doctorA, patientOfB.getId()), "PATIENT_NOT_FOUND");
			expectNotFound(() -> access.requireClinicalAccess(doctorA, patientOfB.getId()), "PATIENT_NOT_FOUND");
		}

		@Test
		void additionalAuthorizationGrantsAccess() {
			patientDoctors.save(new PatientDoctor(patientOfB.getClinicId(), patientOfB.getId(), doctorAProfile.getId(),
					DoctorRelationshipType.AUTHORIZED, owner.userId()));

			assertThat(access.requireClinicalAccess(doctorA, patientOfB.getId())).isEqualTo(patientOfB);
		}

		@Test
		void reachesOwnAppointmentsOnly() {
			assertThat(access.requireAppointment(doctorA, appointmentOfA.getId())).isEqualTo(appointmentOfA);
			expectNotFound(() -> access.requireAppointment(doctorA, appointmentOfB.getId()), "APPOINTMENT_NOT_FOUND");
		}

		@Test
		void reachesOwnDoctorDataOnly() {
			assertThat(access.requireDoctorManagement(doctorA, doctorAProfile.getId())).isEqualTo(doctorAProfile);
			assertThat(access.requireDoctorCalendar(doctorA, doctorAProfile.getId())).isEqualTo(doctorAProfile);

			expectNotFound(() -> access.requireDoctorManagement(doctorA, doctorBProfile.getId()), "DOCTOR_NOT_FOUND");
			expectNotFound(() -> access.requireDoctorCalendar(doctorA, doctorBProfile.getId()), "DOCTOR_NOT_FOUND");
		}

		@Test
		void booksOnlyForThemselvesAndAuthorizedPatients() {
			access.requireCanBook(doctorA, doctorAProfile.getId(), patientOfA.getId());

			expectForbidden(() -> access.requireCanBook(doctorA, doctorBProfile.getId(), patientOfB.getId()));
			expectNotFound(() -> access.requireCanBook(doctorA, doctorAProfile.getId(), patientOfB.getId()),
					"PATIENT_NOT_FOUND");
		}

		@Test
		void scopeIsLimitedToTheirOwnDoctorProfile() {
			DataScope scope = access.dataScope(doctorA);

			assertThat(scope.isClinicWide()).isFalse();
			assertThat(scope.doctorId()).contains(doctorAProfile.getId());
		}

		@Test
		void doctorWithoutAProfileHasNoScope() {
			AuthenticatedUser profileless = principal(clinics.findById(doctorA.clinicId()).orElseThrow(), Role.DOCTOR);

			expectForbidden(() -> access.dataScope(profileless));
			expectNotFound(() -> access.requirePatient(profileless, patientOfA.getId()), "PATIENT_NOT_FOUND");
		}

	}

	@Nested
	class Secretary {

		@Test
		void reachesPatientsForBookingButNeverClinicalData() {
			assertThat(access.requirePatient(secretary, patientOfA.getId())).isEqualTo(patientOfA);

			expectForbidden(() -> access.requireClinicalAccess(secretary, patientOfA.getId()));
			expectForbidden(() -> access.requireClinicalAccess(secretary, patientOfB.getId()));
		}

		@Test
		void operatesTheWholeClinicCalendar() {
			assertThat(access.requireAppointment(secretary, appointmentOfB.getId())).isEqualTo(appointmentOfB);
			assertThat(access.requireDoctorCalendar(secretary, doctorAProfile.getId())).isEqualTo(doctorAProfile);
			access.requireCanBook(secretary, doctorBProfile.getId(), patientOfA.getId());
			assertThat(access.dataScope(secretary).isClinicWide()).isTrue();
		}

		@Test
		void cannotManageDoctorsAndHasNoDoctorProfile() {
			expectForbidden(() -> access.requireDoctorManagement(secretary, doctorAProfile.getId()));
			assertThat(access.ownDoctorId(secretary)).isEmpty();
		}

	}

	@Nested
	class ClinicIsolation {

		@Test
		void otherClinicResourcesDoNotExistForAnyRole() {
			for (AuthenticatedUser user : new AuthenticatedUser[] { owner, doctorA, secretary }) {
				expectNotFound(() -> access.requirePatient(user, otherClinicPatient.getId()), "PATIENT_NOT_FOUND");
				expectNotFound(() -> access.requireAppointment(user, otherClinicAppointment.getId()),
						"APPOINTMENT_NOT_FOUND");
				expectNotFound(() -> access.requireDoctorCalendar(user, otherClinicDoctor.getId()), "DOCTOR_NOT_FOUND");
				expectNotFound(() -> access.requireCanBook(user, otherClinicDoctor.getId(), otherClinicPatient.getId()),
						"DOCTOR_NOT_FOUND");
			}
			expectNotFound(() -> access.requireClinicalAccess(owner, otherClinicPatient.getId()), "PATIENT_NOT_FOUND");
			expectNotFound(() -> access.requireDoctorManagement(owner, otherClinicDoctor.getId()), "DOCTOR_NOT_FOUND");
		}

		@Test
		void ownClinicDoctorCannotBeCombinedWithAnotherClinicsPatient() {
			expectNotFound(() -> access.requireCanBook(owner, doctorAProfile.getId(), otherClinicPatient.getId()),
					"PATIENT_NOT_FOUND");
		}

		@Test
		void unknownIdsAreNotFound() {
			expectNotFound(() -> access.requirePatient(owner, UUID.randomUUID()), "PATIENT_NOT_FOUND");
		}

	}

	private static void expectNotFound(ThrowingCallable call, String code) {
		assertThatThrownBy(call).isInstanceOf(NotFoundException.class)
			.extracting(ex -> ((ApiException) ex).getCode()).isEqualTo(code);
	}

	private static void expectForbidden(ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOf(ForbiddenOperationException.class)
			.extracting(ex -> ((ApiException) ex).getCode()).isEqualTo("FORBIDDEN");
	}

	private AuthenticatedUser principal(Clinic clinic, Role role) {
		String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@access.test";
		AppUser user = users.save(new AppUser(clinic.getId(), "Test", role.name(), email, role));
		return new AuthenticatedUser(user.getId(), clinic.getId(), role, null, true);
	}

	private Doctor doctorProfile(Clinic clinic, AuthenticatedUser user) {
		return doctors.save(new Doctor(clinic.getId(), user.userId(),
				specialties.findByCode("GENERAL_PRACTICE").orElseThrow().getId()));
	}

	private Patient patient(Clinic clinic, Doctor primaryDoctor, AuthenticatedUser createdBy) {
		Patient patient = patients.save(new Patient(clinic.getId(), "Pat", "Ient", LocalDate.of(1980, 1, 1),
				createdBy.userId()));
		patientDoctors.save(new PatientDoctor(clinic.getId(), patient.getId(), primaryDoctor.getId(),
				DoctorRelationshipType.PRIMARY, createdBy.userId()));
		return patient;
	}

	private Appointment appointment(Clinic clinic, Patient patient, Doctor doctor, AuthenticatedUser createdBy) {
		Instant start = Instant.parse("2030-01-07T09:00:00Z").plus(Duration.ofDays(appointments.count()));
		return appointments.save(new Appointment(clinic.getId(), patient.getId(), doctor.getId(), start,
				start.plus(Duration.ofMinutes(30)), AppointmentType.FOLLOW_UP, null, null, createdBy.userId()));
	}

}
