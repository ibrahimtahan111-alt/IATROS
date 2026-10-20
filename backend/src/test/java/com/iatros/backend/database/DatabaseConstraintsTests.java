package com.iatros.backend.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies that the schema itself enforces the integrity rules that must hold even if
 * application code is wrong: clinic isolation, double-booking, and audit immutability.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DatabaseConstraintsTests {

	private static final OffsetDateTime NINE = OffsetDateTime.of(2027, 4, 12, 9, 0, 0, 0, ZoneOffset.UTC);

	@Autowired
	private JdbcTemplate jdbc;

	private SchemaFixtures fixtures;

	private UUID clinicId;

	private UUID ownerId;

	private UUID doctorId;

	private UUID patientId;

	@BeforeEach
	void setUp() {
		fixtures = new SchemaFixtures(jdbc);
		clinicId = fixtures.clinic();
		ownerId = fixtures.user(clinicId, "OWNER");
		doctorId = fixtures.doctor(clinicId);
		patientId = fixtures.patient(clinicId);
	}

	@Nested
	class Appointments {

		@Test
		void overlappingActiveAppointmentsForSameDoctorAreRejected() {
			fixtures.appointment(clinicId, patientId, doctorId, ownerId, NINE, NINE.plusMinutes(30), "SCHEDULED");

			assertThatThrownBy(() -> fixtures.appointment(clinicId, patientId, doctorId, ownerId,
					NINE.plusMinutes(15), NINE.plusMinutes(45), "CONFIRMED"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("appointment_no_overlap");
		}

		@Test
		void backToBackAppointmentsAreAllowed() {
			fixtures.appointment(clinicId, patientId, doctorId, ownerId, NINE, NINE.plusMinutes(30), "SCHEDULED");

			assertThatCode(() -> fixtures.appointment(clinicId, patientId, doctorId, ownerId,
					NINE.plusMinutes(30), NINE.plusMinutes(60), "SCHEDULED"))
				.doesNotThrowAnyException();
		}

		@Test
		void cancelledAndNoShowAppointmentsDoNotBlockTheSlot() {
			fixtures.appointment(clinicId, patientId, doctorId, ownerId, NINE, NINE.plusMinutes(30), "CANCELLED");
			fixtures.appointment(clinicId, patientId, doctorId, ownerId, NINE, NINE.plusMinutes(30), "NO_SHOW");

			assertThatCode(() -> fixtures.appointment(clinicId, patientId, doctorId, ownerId,
					NINE, NINE.plusMinutes(30), "SCHEDULED"))
				.doesNotThrowAnyException();
		}

		@Test
		void differentDoctorsCanBeBookedAtTheSameTime() {
			UUID otherDoctorId = fixtures.doctor(clinicId);
			fixtures.appointment(clinicId, patientId, doctorId, ownerId, NINE, NINE.plusMinutes(30), "SCHEDULED");

			assertThatCode(() -> fixtures.appointment(clinicId, patientId, otherDoctorId, ownerId,
					NINE, NINE.plusMinutes(30), "SCHEDULED"))
				.doesNotThrowAnyException();
		}

		@Test
		void endMustBeAfterStart() {
			assertThatThrownBy(() -> fixtures.appointment(clinicId, patientId, doctorId, ownerId,
					NINE, NINE, "SCHEDULED"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("appointment_time_order");
		}

	}

	@Nested
	class ClinicIsolation {

		@Test
		void appointmentCannotReferenceAPatientFromAnotherClinic() {
			UUID otherClinicPatient = fixtures.patient(fixtures.clinic());

			assertThatThrownBy(() -> fixtures.appointment(clinicId, otherClinicPatient, doctorId, ownerId,
					NINE, NINE.plusMinutes(30), "SCHEDULED"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("appointment_patient_fk");
		}

		@Test
		void appointmentCannotReferenceADoctorFromAnotherClinic() {
			UUID otherClinicDoctor = fixtures.doctor(fixtures.clinic());

			assertThatThrownBy(() -> fixtures.appointment(clinicId, patientId, otherClinicDoctor, ownerId,
					NINE, NINE.plusMinutes(30), "SCHEDULED"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("appointment_doctor_fk");
		}

		@Test
		void patientCannotBeLinkedToADoctorFromAnotherClinic() {
			UUID otherClinicDoctor = fixtures.doctor(fixtures.clinic());

			assertThatThrownBy(() -> fixtures.linkDoctor(clinicId, patientId, otherClinicDoctor, "AUTHORIZED"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("patient_doctor_doctor_fk");
		}

		@Test
		void doctorProfileCannotUseAUserFromAnotherClinic() {
			UUID otherClinicUser = fixtures.user(fixtures.clinic(), "DOCTOR");

			assertThatThrownBy(() -> fixtures.doctor(clinicId, otherClinicUser))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("doctor_user_fk");
		}

	}

	@Nested
	class PatientDoctorRelationships {

		@Test
		void patientHasAtMostOnePrimaryDoctor() {
			fixtures.linkDoctor(clinicId, patientId, doctorId, "PRIMARY");
			UUID secondDoctor = fixtures.doctor(clinicId);

			assertThatThrownBy(() -> fixtures.linkDoctor(clinicId, patientId, secondDoctor, "PRIMARY"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("patient_doctor_one_primary_uk");
		}

		@Test
		void patientCanHaveSeveralAuthorizedDoctors() {
			fixtures.linkDoctor(clinicId, patientId, doctorId, "PRIMARY");

			assertThatCode(() -> {
				fixtures.linkDoctor(clinicId, patientId, fixtures.doctor(clinicId), "AUTHORIZED");
				fixtures.linkDoctor(clinicId, patientId, fixtures.doctor(clinicId), "AUTHORIZED");
			}).doesNotThrowAnyException();
		}

		@Test
		void sameDoctorCannotBeLinkedTwice() {
			fixtures.linkDoctor(clinicId, patientId, doctorId, "PRIMARY");

			assertThatThrownBy(() -> fixtures.linkDoctor(clinicId, patientId, doctorId, "AUTHORIZED"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("patient_doctor_uk");
		}

	}

	@Nested
	class Schedules {

		@Test
		void overlappingPeriodsOnTheSameDayAreRejected() {
			fixtures.schedulePeriod(doctorId, 1, LocalTime.of(9, 0), LocalTime.of(12, 0));

			assertThatThrownBy(() -> fixtures.schedulePeriod(doctorId, 1, LocalTime.of(11, 0), LocalTime.of(14, 0)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("doctor_schedule_period_no_overlap");
		}

		@Test
		void splitDayAndOtherWeekdaysAreAllowed() {
			assertThatCode(() -> {
				fixtures.schedulePeriod(doctorId, 1, LocalTime.of(9, 0), LocalTime.of(12, 0));
				fixtures.schedulePeriod(doctorId, 1, LocalTime.of(14, 0), LocalTime.of(18, 0));
				fixtures.schedulePeriod(doctorId, 2, LocalTime.of(9, 0), LocalTime.of(12, 0));
			}).doesNotThrowAnyException();
		}

	}

	@Nested
	class Users {

		@Test
		void emailIsUniqueRegardlessOfCase() {
			jdbc.update("""
					INSERT INTO app_user (id, clinic_id, first_name, last_name, email, role)
					VALUES (?, ?, 'A', 'B', 'Dr.Martin@Example.test', 'DOCTOR')
					""", UUID.randomUUID(), clinicId);

			assertThatThrownBy(() -> jdbc.update("""
					INSERT INTO app_user (id, clinic_id, first_name, last_name, email, role)
					VALUES (?, ?, 'A', 'B', 'dr.martin@example.TEST', 'SECRETARY')
					""", UUID.randomUUID(), fixtures.clinic()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("app_user_email_uk");
		}

		@Test
		void activeUserMustHaveAPassword() {
			assertThatThrownBy(() -> jdbc.update("""
					INSERT INTO app_user (id, clinic_id, first_name, last_name, email, role, status)
					VALUES (?, ?, 'A', 'B', 'no-password@example.test', 'SECRETARY', 'ACTIVE')
					""", UUID.randomUUID(), clinicId))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("app_user_active_requires_password");
		}

	}

	@Nested
	class Audit {

		@Test
		void auditEventsCannotBeModified() {
			UUID eventId = UUID.randomUUID();
			jdbc.update("INSERT INTO audit_event (id, clinic_id, action, entity_type) VALUES (?, ?, 'TEST', 'TEST')",
					eventId, clinicId);

			assertThatThrownBy(() -> jdbc.update("UPDATE audit_event SET action = 'CHANGED' WHERE id = ?", eventId))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("audit_event rows are immutable");
		}

		@Test
		void auditEventsCannotBeDeleted() {
			UUID eventId = UUID.randomUUID();
			jdbc.update("INSERT INTO audit_event (id, clinic_id, action, entity_type) VALUES (?, ?, 'TEST', 'TEST')",
					eventId, clinicId);

			assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE id = ?", eventId))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("audit_event rows are immutable");
		}

	}

	@Nested
	class ReferenceData {

		@Test
		void specialtiesAreSeededWithStableCodes() {
			Integer count = jdbc.queryForObject(
					"SELECT count(*) FROM specialty WHERE code IN ('GENERAL_PRACTICE', 'CARDIOLOGY', 'OTHER')",
					Integer.class);
			assertThat(count).isEqualTo(3);
		}

	}

}
