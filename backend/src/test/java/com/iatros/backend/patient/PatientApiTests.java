package com.iatros.backend.patient;

import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.appointment.Appointment;
import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.appointment.AppointmentType;
import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.security.RateLimiter;
import com.iatros.backend.support.TestClinicFixture;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** Patient management (roadmap.md Phase 7, api.md §10, decisions.md D-036): scoped access and doctor links. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PatientApiTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private TestClinicFixture fixture;
	@Autowired private RateLimiter rateLimiter;
	@Autowired private PatientRepository patients;
	@Autowired private PatientDoctorRepository patientDoctors;
	@Autowired private AppointmentRepository appointments;
	@Autowired private JdbcTemplate jdbc;

	private Clinic clinic;
	private AppUser owner;
	private AppUser daniel;
	private Doctor cardiologist;
	private Doctor dermatologist;
	private Doctor otherClinicDoctor;
	private Patient alice;
	private Patient bruno;
	private Patient otherClinicPatient;
	private Cookie ownerSession;
	private Cookie secretarySession;
	private Cookie cardiologistSession;
	private Cookie dermatologistSession;

	@BeforeEach
	void setUp() throws Exception {
		rateLimiter.reset();
		clinic = fixture.clinic("Patient Clinic");
		owner = fixture.activeUser(clinic, Role.OWNER, "Olivia", "Owner");
		AppUser secretary = fixture.activeUser(clinic, Role.SECRETARY, "Sara", "Adams");
		daniel = fixture.activeUser(clinic, Role.DOCTOR, "Daniel", "Brown");
		AppUser chloe = fixture.activeUser(clinic, Role.DOCTOR, "Chloe", "Carter");
		cardiologist = fixture.doctorProfile(clinic, daniel, "CARDIOLOGY");
		dermatologist = fixture.doctorProfile(clinic, chloe, "DERMATOLOGY");
		alice = patient(clinic, "Alice", "Martin", cardiologist);
		bruno = patient(clinic, "Bruno", "Dupont", dermatologist);
		Clinic otherClinic = fixture.clinic("Other Clinic");
		otherClinicDoctor = fixture.doctorProfile(otherClinic,
				fixture.activeUser(otherClinic, Role.DOCTOR, "Oscar", "Abbott"), "CARDIOLOGY");
		otherClinicPatient = patient(otherClinic, "Alice", "Zola", otherClinicDoctor);
		ownerSession = fixture.login(mockMvc, owner);
		secretarySession = fixture.login(mockMvc, secretary);
		cardiologistSession = fixture.login(mockMvc, daniel);
		dermatologistSession = fixture.login(mockMvc, chloe);
	}

	@Nested
	class Listing {

		@Test
		void ownersAndSecretariesSeeEveryPatientOfTheirClinicOnly() throws Exception {
			for (Cookie session : new Cookie[] { ownerSession, secretarySession }) {
				mockMvc.perform(get("/api/v1/patients").cookie(session))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[*].lastName").value(contains("Dupont", "Martin")))
					.andExpect(jsonPath("$.content[1].primaryDoctor.id").value(cardiologist.getId().toString()))
					.andExpect(jsonPath("$.content[1].primaryDoctor.relationshipType").value("PRIMARY"));
			}
		}

		@Test
		void aDoctorSeesOnlyPatientsTheyArePrimaryOrAuthorizedFor() throws Exception {
			mockMvc.perform(get("/api/v1/patients").cookie(cardiologistSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(alice.getId().toString())));
			link(bruno, cardiologist, DoctorRelationshipType.AUTHORIZED);
			mockMvc.perform(get("/api/v1/patients").cookie(cardiologistSession))
				.andExpect(jsonPath("$.content[*].id")
					.value(contains(bruno.getId().toString(), alice.getId().toString())));
		}

		@Test
		void searchesFiltersAndSorts() throws Exception {
			mockMvc.perform(get("/api/v1/patients").param("search", "ALI").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(alice.getId().toString())));
			mockMvc.perform(get("/api/v1/patients").param("primaryDoctorId", dermatologist.getId().toString())
					.cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(bruno.getId().toString())));
			mockMvc.perform(get("/api/v1/patients").param("sort", "firstName,desc").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].firstName").value(contains("Bruno", "Alice")));
			mockMvc.perform(get("/api/v1/patients").param("sort", "email").cookie(ownerSession))
				.andExpect(status().isBadRequest());
		}

		@Test
		void hidesArchivedPatientsUnlessRequested() throws Exception {
			bruno.archive(Instant.now(), owner.getId());
			fixture.flush();
			mockMvc.perform(get("/api/v1/patients").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(alice.getId().toString())));
			mockMvc.perform(get("/api/v1/patients").param("status", "ARCHIVED").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(bruno.getId().toString())));
		}

		@Test
		void requiresAuthentication() throws Exception {
			mockMvc.perform(get("/api/v1/patients")).andExpect(status().isUnauthorized());
		}

	}

	@Nested
	class Reading {

		@Test
		void ownersSeeEveryLinkedDoctor() throws Exception {
			link(alice, dermatologist, DoctorRelationshipType.AUTHORIZED);
			mockMvc.perform(get("/api/v1/patients/{id}", alice.getId()).cookie(ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.primaryDoctor.id").value(cardiologist.getId().toString()))
				.andExpect(jsonPath("$.doctors[*].relationshipType").value(contains("PRIMARY", "AUTHORIZED")))
				.andExpect(jsonPath("$.version").value(0));
		}

		@Test
		void secretariesSeeOnlyThePrimaryDoctor() throws Exception {
			mockMvc.perform(get("/api/v1/patients/{id}", alice.getId()).cookie(secretarySession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.primaryDoctor.lastName").value("Brown"))
				.andExpect(jsonPath("$.doctors").doesNotExist());
		}

		@Test
		void hidesUnlinkedAndOtherClinicPatients() throws Exception {
			mockMvc.perform(get("/api/v1/patients/{id}", bruno.getId()).cookie(cardiologistSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
			mockMvc.perform(get("/api/v1/patients/{id}", otherClinicPatient.getId()).cookie(ownerSession))
				.andExpect(status().isNotFound());
		}

	}

	@Nested
	class Creating {

		@Test
		void createsThePatientWithAMedicalRecordAndPrimaryDoctor() throws Exception {
			String response = create(secretarySession, "  Emma  ", "1990-05-01", cardiologist.getId())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.firstName").value("Emma"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.primaryDoctor.id").value(cardiologist.getId().toString()))
				.andReturn().getResponse().getContentAsString();
			UUID patientId = UUID.fromString(JsonPath.read(response, "$.id"));
			fixture.flush();
			assertThat(jdbc.queryForObject("select count(*) from medical_record where patient_id = ?", Integer.class,
					patientId)).isEqualTo(1);
			assertThat(auditCount("PATIENT_CREATED", patientId)).isEqualTo(1);
		}

		@Test
		void aDoctorRegistersPatientsOnlyForThemselves() throws Exception {
			create(cardiologistSession, "Emma", "1990-05-01", cardiologist.getId()).andExpect(status().isCreated());
			create(cardiologistSession, "Emma", "1990-05-01", dermatologist.getId())
				.andExpect(status().isForbidden());
		}

		@Test
		void rejectsUnknownOtherClinicAndDeactivatedDoctors() throws Exception {
			create(ownerSession, "Emma", "1990-05-01", otherClinicDoctor.getId())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'primaryDoctorId')].code").value(contains("INVALID")));
			daniel.deactivate(Instant.now());
			fixture.flush();
			create(ownerSession, "Emma", "1990-05-01", cardiologist.getId())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'primaryDoctorId')].code")
					.value(contains("DOCTOR_DEACTIVATED")));
		}

		@Test
		void validatesTheBirthDate() throws Exception {
			create(ownerSession, "Emma", LocalDate.now().plusDays(2).toString(), cardiologist.getId())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'dateOfBirth')].code").value(contains("DATE_IN_FUTURE")));
			create(ownerSession, "Emma", "1850-01-01", cardiologist.getId())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'dateOfBirth')].code").value(contains("OUT_OF_RANGE")));
		}

		@Test
		void validatesRequiredFields() throws Exception {
			mockMvc.perform(post("/api/v1/patients").with(spaCsrf()).cookie(ownerSession)
					.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field")
					.value(containsInAnyOrder("firstName", "lastName", "dateOfBirth", "primaryDoctorId")));
		}

	}

	@Nested
	class Editing {

		@Test
		void everyRoleEditsPatientsInItsScope() throws Exception {
			update(secretarySession, alice.getId(), "Alicia", 0).andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value("Alicia"))
				.andExpect(jsonPath("$.phone").value("+33 6 00 00 00 00"))
				.andExpect(jsonPath("$.address").doesNotExist());
			update(cardiologistSession, alice.getId(), "Alice", 1).andExpect(status().isOk());
			update(cardiologistSession, bruno.getId(), "Bruno", 0).andExpect(status().isNotFound());
			update(ownerSession, otherClinicPatient.getId(), "Alice", 0).andExpect(status().isNotFound());
		}

		@Test
		void theAuditTrailNamesTheChangedFieldsButNeverTheirValues() throws Exception {
			update(secretarySession, alice.getId(), "Alicia", 0).andExpect(status().isOk());
			fixture.flush();
			String metadata = jdbc.queryForObject(
					"select metadata::text from audit_event where action = 'PATIENT_UPDATED' and entity_id = ?",
					String.class, alice.getId());
			assertThat(metadata).contains("\"changedFields\": [\"firstName\", \"phone\"]")
				.doesNotContain("Alicia")
				.doesNotContain("+33");
		}

		@Test
		void rejectsAStaleVersion() throws Exception {
			update(ownerSession, alice.getId(), "Alicia", 5)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
		}

		@Test
		void archivedPatientsAreReadOnly() throws Exception {
			alice.archive(Instant.now(), owner.getId());
			fixture.flush();
			update(ownerSession, alice.getId(), "Alicia", alice.getVersion())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"));
		}

	}

	@Nested
	class Archiving {

		@Test
		void anOwnerArchivesAndRestores() throws Exception {
			changeStatus(ownerSession, alice.getId(), "ARCHIVED")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ARCHIVED"))
				.andExpect(jsonPath("$.archivedAt").isNotEmpty());
			changeStatus(ownerSession, alice.getId(), "ACTIVE")
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.archivedAt").doesNotExist());
			assertThat(auditCount("PATIENT_ARCHIVED", alice.getId())).isEqualTo(1);
			assertThat(auditCount("PATIENT_RESTORED", alice.getId())).isEqualTo(1);
		}

		@Test
		void doctorsAndSecretariesCannotArchive() throws Exception {
			changeStatus(secretarySession, alice.getId(), "ARCHIVED").andExpect(status().isForbidden());
			changeStatus(cardiologistSession, alice.getId(), "ARCHIVED").andExpect(status().isForbidden());
		}

		@Test
		void patientsWithUpcomingAppointmentsCannotBeArchived() throws Exception {
			Instant start = Instant.now().plus(Duration.ofDays(1));
			appointments.save(new Appointment(clinic.getId(), alice.getId(), cardiologist.getId(), start,
					start.plus(Duration.ofMinutes(30)), AppointmentType.FOLLOW_UP, null, null, owner.getId()));
			fixture.flush();
			changeStatus(ownerSession, alice.getId(), "ARCHIVED")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PATIENT_HAS_UPCOMING_APPOINTMENTS"));
		}

		@Test
		void cannotArchiveAnotherClinicsPatient() throws Exception {
			changeStatus(ownerSession, otherClinicPatient.getId(), "ARCHIVED").andExpect(status().isNotFound());
		}

	}

	@Nested
	class DoctorLinks {

		@Test
		void changingThePrimaryDoctorKeepsThePreviousOneAuthorized() throws Exception {
			mockMvc.perform(patch("/api/v1/patients/{id}/primary-doctor", alice.getId()).with(spaCsrf())
					.cookie(ownerSession).contentType(MediaType.APPLICATION_JSON)
					.content(doctorBody(dermatologist.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.primaryDoctor.id").value(dermatologist.getId().toString()))
				.andExpect(jsonPath("$.doctors", hasSize(2)))
				.andExpect(jsonPath("$.doctors[1].id").value(cardiologist.getId().toString()))
				.andExpect(jsonPath("$.doctors[1].relationshipType").value("AUTHORIZED"));
			mockMvc.perform(get("/api/v1/patients/{id}", alice.getId()).cookie(cardiologistSession))
				.andExpect(status().isOk());
			mockMvc.perform(get("/api/v1/patients/{id}", alice.getId()).cookie(dermatologistSession))
				.andExpect(status().isOk());
		}

		@Test
		void promotesAnAlreadyAuthorizedDoctor() throws Exception {
			link(alice, dermatologist, DoctorRelationshipType.AUTHORIZED);
			mockMvc.perform(patch("/api/v1/patients/{id}/primary-doctor", alice.getId()).with(spaCsrf())
					.cookie(ownerSession).contentType(MediaType.APPLICATION_JSON)
					.content(doctorBody(dermatologist.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.primaryDoctor.id").value(dermatologist.getId().toString()))
				.andExpect(jsonPath("$.doctors", hasSize(2)));
		}

		@Test
		void addsAndRemovesAuthorizedDoctors() throws Exception {
			addDoctor(ownerSession, alice.getId(), dermatologist.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.doctors[*].id").value(
						contains(cardiologist.getId().toString(), dermatologist.getId().toString())));
			mockMvc.perform(get("/api/v1/patients/{id}", alice.getId()).cookie(dermatologistSession))
				.andExpect(status().isOk());
			addDoctor(ownerSession, alice.getId(), dermatologist.getId())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCTOR_ALREADY_AUTHORIZED"));

			removeDoctor(ownerSession, alice.getId(), dermatologist.getId()).andExpect(status().isNoContent());
			mockMvc.perform(get("/api/v1/patients/{id}", alice.getId()).cookie(dermatologistSession))
				.andExpect(status().isNotFound());
			removeDoctor(ownerSession, alice.getId(), dermatologist.getId()).andExpect(status().isNotFound());
		}

		@Test
		void thePrimaryDoctorCannotBeRemoved() throws Exception {
			removeDoctor(ownerSession, alice.getId(), cardiologist.getId())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PRIMARY_DOCTOR_REQUIRED"));
		}

		@Test
		void onlyOwnersManageLinks() throws Exception {
			addDoctor(cardiologistSession, alice.getId(), dermatologist.getId()).andExpect(status().isForbidden());
			addDoctor(secretarySession, alice.getId(), dermatologist.getId()).andExpect(status().isForbidden());
			removeDoctor(cardiologistSession, alice.getId(), cardiologist.getId()).andExpect(status().isForbidden());
		}

		@Test
		void rejectsOtherClinicDoctorsAndPatients() throws Exception {
			addDoctor(ownerSession, alice.getId(), otherClinicDoctor.getId())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'doctorId')].code").value(contains("INVALID")));
			addDoctor(ownerSession, otherClinicPatient.getId(), dermatologist.getId())
				.andExpect(status().isNotFound());
		}

		@Test
		void archivedPatientsKeepTheirLinks() throws Exception {
			alice.archive(Instant.now(), owner.getId());
			fixture.flush();
			addDoctor(ownerSession, alice.getId(), dermatologist.getId())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"));
		}

	}

	private Patient patient(Clinic owningClinic, String firstName, String lastName, Doctor primary) {
		Patient patient = patients.save(new Patient(owningClinic.getId(), firstName, lastName,
				LocalDate.of(1980, 1, 1), primary.getUserId()));
		link(patient, primary, DoctorRelationshipType.PRIMARY);
		return patient;
	}

	private void link(Patient patient, Doctor doctor, DoctorRelationshipType type) {
		patientDoctors.save(new PatientDoctor(patient.getClinicId(), patient.getId(), doctor.getId(), type,
				doctor.getUserId()));
		fixture.flush();
	}

	private int auditCount(String action, UUID patientId) {
		fixture.flush();
		return jdbc.queryForObject("select count(*) from audit_event where action = ? and entity_id = ?",
				Integer.class, action, patientId);
	}

	private ResultActions create(Cookie session, String firstName, String dateOfBirth, UUID primaryDoctorId)
			throws Exception {
		String body = """
				{"firstName": "%s", "lastName": "Petit", "dateOfBirth": "%s", "gender": "FEMALE",
				 "phone": "+33 6 11 11 11 11", "primaryDoctorId": "%s"}"""
			.formatted(firstName, dateOfBirth, primaryDoctorId);
		return mockMvc.perform(post("/api/v1/patients").with(spaCsrf()).cookie(session)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions update(Cookie session, UUID patientId, String firstName, long version) throws Exception {
		String body = """
				{"firstName": "%s", "lastName": "Martin", "dateOfBirth": "1980-01-01",
				 "phone": " +33 6 00 00 00 00 ", "address": "  ", "version": %d}"""
			.formatted(firstName, version);
		return mockMvc.perform(put("/api/v1/patients/{id}", patientId).with(spaCsrf()).cookie(session)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions changeStatus(Cookie session, UUID patientId, String status) throws Exception {
		return mockMvc.perform(patch("/api/v1/patients/{id}/status", patientId).with(spaCsrf()).cookie(session)
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + status + "\"}"));
	}

	private ResultActions addDoctor(Cookie session, UUID patientId, UUID doctorId) throws Exception {
		return mockMvc.perform(post("/api/v1/patients/{id}/doctors", patientId).with(spaCsrf()).cookie(session)
			.contentType(MediaType.APPLICATION_JSON).content(doctorBody(doctorId)));
	}

	private ResultActions removeDoctor(Cookie session, UUID patientId, UUID doctorId) throws Exception {
		return mockMvc.perform(delete("/api/v1/patients/{id}/doctors/{doctorId}", patientId, doctorId)
			.with(spaCsrf()).cookie(session));
	}

	private static String doctorBody(UUID doctorId) {
		return "{\"doctorId\": \"" + doctorId + "\"}";
	}

}
