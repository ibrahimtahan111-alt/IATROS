package com.iatros.backend.database;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts minimal, fictional rows directly through SQL to exercise database constraints. */
class SchemaFixtures {

	private final JdbcTemplate jdbc;

	SchemaFixtures(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	UUID clinic() {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO clinic (id, name) VALUES (?, ?)", id, "Test Clinic " + id);
		return id;
	}

	UUID user(UUID clinicId, String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO app_user (id, clinic_id, first_name, last_name, email, role, status, password_hash)
				VALUES (?, ?, 'Test', 'User', ?, ?, 'ACTIVE', 'test-hash')
				""", id, clinicId, id + "@example.test", role);
		return id;
	}

	UUID doctor(UUID clinicId, UUID userId) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO doctor (id, clinic_id, user_id, primary_specialty_id)
				SELECT ?, ?, ?, id FROM specialty WHERE code = 'GENERAL_PRACTICE'
				""", id, clinicId, userId);
		return id;
	}

	UUID doctor(UUID clinicId) {
		return doctor(clinicId, user(clinicId, "DOCTOR"));
	}

	UUID patient(UUID clinicId) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO patient (id, clinic_id, first_name, last_name, date_of_birth)
				VALUES (?, ?, 'Jane', 'Doe', ?)
				""", id, clinicId, LocalDate.of(1985, 6, 15));
		return id;
	}

	void linkDoctor(UUID clinicId, UUID patientId, UUID doctorId, String relationshipType) {
		jdbc.update("""
				INSERT INTO patient_doctor (id, clinic_id, patient_id, doctor_id, relationship_type)
				VALUES (?, ?, ?, ?, ?)
				""", UUID.randomUUID(), clinicId, patientId, doctorId, relationshipType);
	}

	UUID appointment(UUID clinicId, UUID patientId, UUID doctorId, UUID createdBy,
			OffsetDateTime startAt, OffsetDateTime endAt, String status) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO appointment (id, clinic_id, patient_id, doctor_id, start_at, end_at,
				                         appointment_type, status, created_by)
				VALUES (?, ?, ?, ?, ?, ?, 'ROUTINE', ?, ?)
				""", id, clinicId, patientId, doctorId, startAt, endAt, status, createdBy);
		return id;
	}

	void schedulePeriod(UUID doctorId, int dayOfWeek, LocalTime start, LocalTime end) {
		jdbc.update("""
				INSERT INTO doctor_schedule_period (id, doctor_id, day_of_week, start_time, end_time)
				VALUES (?, ?, ?, ?, ?)
				""", UUID.randomUUID(), doctorId, dayOfWeek, start, end);
	}

}
