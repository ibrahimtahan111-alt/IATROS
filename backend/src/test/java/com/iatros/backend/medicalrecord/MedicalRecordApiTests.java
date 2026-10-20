package com.iatros.backend.medicalrecord;

import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.patient.DoctorRelationshipType;
import com.iatros.backend.patient.Patient;
import com.iatros.backend.patient.PatientDoctor;
import com.iatros.backend.patient.PatientDoctorRepository;
import com.iatros.backend.patient.PatientRepository;
import com.iatros.backend.security.RateLimiter;
import com.iatros.backend.support.TestClinicFixture;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** Medical record sections and notes (roadmap.md Phase 8, api.md §12–15, decisions.md D-037). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MedicalRecordApiTests {

	private static final String RECORD = "/api/v1/patients/{patientId}/medical-record";

	@Autowired private MockMvc mockMvc;
	@Autowired private TestClinicFixture fixture;
	@Autowired private RateLimiter rateLimiter;
	@Autowired private PatientRepository patients;
	@Autowired private PatientDoctorRepository patientDoctors;
	@Autowired private MedicalRecordRepository records;
	@Autowired private JdbcTemplate jdbc;

	private AppUser owner;
	private Doctor dermatologist;
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
		Clinic clinic = fixture.clinic("Record Clinic");
		owner = fixture.activeUser(clinic, Role.OWNER, "Olivia", "Owner");
		AppUser secretary = fixture.activeUser(clinic, Role.SECRETARY, "Sara", "Adams");
		AppUser daniel = fixture.activeUser(clinic, Role.DOCTOR, "Daniel", "Brown");
		AppUser chloe = fixture.activeUser(clinic, Role.DOCTOR, "Chloe", "Carter");
		Doctor cardiologist = fixture.doctorProfile(clinic, daniel, "CARDIOLOGY");
		dermatologist = fixture.doctorProfile(clinic, chloe, "DERMATOLOGY");
		alice = patient(clinic, "Alice", cardiologist);
		bruno = patient(clinic, "Bruno", dermatologist);
		Clinic otherClinic = fixture.clinic("Other Clinic");
		Doctor otherDoctor = fixture.doctorProfile(otherClinic,
				fixture.activeUser(otherClinic, Role.DOCTOR, "Oscar", "Abbott"), "CARDIOLOGY");
		otherClinicPatient = patient(otherClinic, "Zoe", otherDoctor);
		ownerSession = fixture.login(mockMvc, owner);
		secretarySession = fixture.login(mockMvc, secretary);
		cardiologistSession = fixture.login(mockMvc, daniel);
		dermatologistSession = fixture.login(mockMvc, chloe);
	}

	@Nested
	class Access {

		@Test
		void ownersAndLinkedDoctorsReadTheRecord() throws Exception {
			for (Cookie session : new Cookie[] { ownerSession, cardiologistSession }) {
				mockMvc.perform(get(RECORD, alice.getId()).cookie(session))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.patientId").value(alice.getId().toString()))
					.andExpect(jsonPath("$.allergies").isArray());
			}
		}

		@Test
		void secretariesNeverAccessClinicalData() throws Exception {
			mockMvc.perform(get(RECORD, alice.getId()).cookie(secretarySession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
			mockMvc.perform(get(RECORD + "/notes", alice.getId()).cookie(secretarySession))
				.andExpect(status().isForbidden());
			addAllergy(secretarySession, alice.getId(), "Penicillin").andExpect(status().isForbidden());
		}

		@Test
		void unlinkedDoctorsAndOtherClinicsGetNotFound() throws Exception {
			mockMvc.perform(get(RECORD, alice.getId()).cookie(dermatologistSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
			addAllergy(dermatologistSession, alice.getId(), "Penicillin").andExpect(status().isNotFound());
			mockMvc.perform(get(RECORD, otherClinicPatient.getId()).cookie(ownerSession))
				.andExpect(status().isNotFound());
		}

		@Test
		void additionallyAuthorizedDoctorsGainAccess() throws Exception {
			link(alice, dermatologist, DoctorRelationshipType.AUTHORIZED);
			mockMvc.perform(get(RECORD, alice.getId()).cookie(dermatologistSession)).andExpect(status().isOk());
		}

		@Test
		void requiresAuthentication() throws Exception {
			mockMvc.perform(get(RECORD, alice.getId())).andExpect(status().isUnauthorized());
		}

		@Test
		void entriesAreScopedToTheirPatient() throws Exception {
			String allergyId = id(addAllergy(ownerSession, alice.getId(), "Latex"));
			mockMvc.perform(put(RECORD + "/allergies/{id}", bruno.getId(), allergyId).with(spaCsrf())
					.cookie(ownerSession).contentType(MediaType.APPLICATION_JSON)
					.content("{\"allergen\": \"Latex\", \"version\": 0}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ALLERGY_NOT_FOUND"));
		}

		@Test
		void archivedPatientsRecordsAreReadOnly() throws Exception {
			addAllergy(ownerSession, alice.getId(), "Latex").andExpect(status().isCreated());
			alice.archive(Instant.now(), owner.getId());
			fixture.flush();
			mockMvc.perform(get(RECORD, alice.getId()).cookie(ownerSession))
				.andExpect(jsonPath("$.allergies", hasSize(1)));
			addAllergy(ownerSession, alice.getId(), "Pollen")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"));
		}

	}

	@Nested
	class StructuredSections {

		@Test
		void allergyLifecycleWithOptimisticLockingAndSoftDelete() throws Exception {
			String allergyId = id(addAllergy(cardiologistSession, alice.getId(), "  Penicillin  ")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.allergen").value("Penicillin"))
				.andExpect(jsonPath("$.severity").value("SEVERE"))
				.andExpect(jsonPath("$.version").value(0)));

			String update = """
					{"allergen": "Penicillin", "reaction": "Hives", "severity": "MODERATE", "version": %d}""";
			mockMvc.perform(put(RECORD + "/allergies/{id}", alice.getId(), allergyId).with(spaCsrf())
					.cookie(ownerSession).contentType(MediaType.APPLICATION_JSON).content(update.formatted(0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reaction").value("Hives"))
				.andExpect(jsonPath("$.version").value(1));
			mockMvc.perform(put(RECORD + "/allergies/{id}", alice.getId(), allergyId).with(spaCsrf())
					.cookie(ownerSession).contentType(MediaType.APPLICATION_JSON).content(update.formatted(0)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
			mockMvc.perform(put(RECORD + "/allergies/{id}", alice.getId(), allergyId).with(spaCsrf())
					.cookie(ownerSession).contentType(MediaType.APPLICATION_JSON)
					.content("{\"allergen\": \"Penicillin\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'version')].code").value(contains("REQUIRED")));

			mockMvc.perform(delete(RECORD + "/allergies/{id}", alice.getId(), allergyId).with(spaCsrf())
					.cookie(cardiologistSession))
				.andExpect(status().isNoContent());
			mockMvc.perform(get(RECORD, alice.getId()).cookie(ownerSession))
				.andExpect(jsonPath("$.allergies", hasSize(0)));
			mockMvc.perform(delete(RECORD + "/allergies/{id}", alice.getId(), allergyId).with(spaCsrf())
					.cookie(cardiologistSession))
				.andExpect(status().isNotFound());

			UUID entityId = UUID.fromString(allergyId);
			assertThat(auditCount(AuditActions.MEDICAL_ENTRY_CREATED, entityId)).isEqualTo(1);
			assertThat(auditCount(AuditActions.MEDICAL_ENTRY_UPDATED, entityId)).isEqualTo(1);
			assertThat(auditCount(AuditActions.MEDICAL_ENTRY_DELETED, entityId)).isEqualTo(1);
			assertThat(jdbc.queryForObject("select deleted_at is not null from allergy where id = ?", Boolean.class,
					entityId)).isTrue();
		}

		@ParameterizedTest(name = "{0}")
		@MethodSource("com.iatros.backend.medicalrecord.MedicalRecordApiTests#sections")
		void sectionLifecycleWithValidationLockingScopingAndSoftDelete(String section, String table, String code,
				String created, String updated, String invalid, String invalidField) throws Exception {
			String path = RECORD + "/" + section;
			String entryId = id(send(post(path, alice.getId()), cardiologistSession, created)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.version").value(0)));
			String one = path + "/{id}";

			send(put(one, alice.getId(), entryId), ownerSession, updated.formatted(0))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1));
			send(put(one, alice.getId(), entryId), ownerSession, updated.formatted(0))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
			send(put(one, alice.getId(), entryId), ownerSession, invalid)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value(invalidField));
			send(put(one, bruno.getId(), entryId), ownerSession, updated.formatted(1))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(code));
			mockMvc.perform(delete(one, bruno.getId(), entryId).with(spaCsrf()).cookie(ownerSession))
				.andExpect(status().isNotFound());
			mockMvc.perform(delete(one, alice.getId(), entryId).with(spaCsrf()).cookie(dermatologistSession))
				.andExpect(status().isNotFound());

			mockMvc.perform(delete(one, alice.getId(), entryId).with(spaCsrf()).cookie(cardiologistSession))
				.andExpect(status().isNoContent());
			mockMvc.perform(delete(one, alice.getId(), entryId).with(spaCsrf()).cookie(cardiologistSession))
				.andExpect(status().isNotFound());

			UUID entityId = UUID.fromString(entryId);
			assertThat(auditCount(AuditActions.MEDICAL_ENTRY_CREATED, entityId)).isEqualTo(1);
			assertThat(auditCount(AuditActions.MEDICAL_ENTRY_UPDATED, entityId)).isEqualTo(1);
			assertThat(auditCount(AuditActions.MEDICAL_ENTRY_DELETED, entityId)).isEqualTo(1);
			assertThat(jdbc.queryForObject("select deleted_at is not null from " + table + " where id = ?",
					Boolean.class, entityId)).isTrue();
		}

		@Test
		void validatesRequiredFields() throws Exception {
			addAllergy(ownerSession, alice.getId(), "  ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("allergen"));
		}

		@Test
		void conditionsAreOrderedActiveFirstAndCannotBeDiagnosedInTheFuture() throws Exception {
			addCondition("Asthma", "RESOLVED", null).andExpect(status().isCreated());
			addCondition("Hypertension", "ACTIVE", "2020-03-01").andExpect(status().isCreated());
			addCondition("Diabetes", "ACTIVE", LocalDate.now(ZoneId.of("Europe/Paris")).plusDays(2).toString())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'diagnosedOn')].code")
					.value(contains("DATE_IN_FUTURE")));
			mockMvc.perform(get(RECORD, alice.getId()).cookie(ownerSession))
				.andExpect(jsonPath("$.conditions[*].name").value(contains("Hypertension", "Asthma")));
		}

		@Test
		void medicationsRejectAnEndBeforeTheStartAndListActiveFirst() throws Exception {
			addMedication("Zestril", "2024-01-01", "2023-01-01", true)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'endDate')].code").value(contains("END_BEFORE_START")));
			addMedication("Aspirin", "2023-01-01", "2023-06-01", false).andExpect(status().isCreated());
			addMedication("Metformin", "2024-01-01", null, true).andExpect(status().isCreated());
			mockMvc.perform(get(RECORD, alice.getId()).cookie(ownerSession))
				.andExpect(jsonPath("$.medications[*].name").value(contains("Metformin", "Aspirin")))
				.andExpect(jsonPath("$.medications[0].active").value(true));
		}

		@Test
		void historyIsMostRecentFirstWithUndatedEntriesLast() throws Exception {
			addHistory("Appendectomy", "2001-05-01");
			addHistory("Childhood asthma", null);
			addHistory("Knee surgery", "2015-09-10");
			mockMvc.perform(get(RECORD, alice.getId()).cookie(cardiologistSession))
				.andExpect(jsonPath("$.history[*].description")
					.value(contains("Knee surgery", "Appendectomy", "Childhood asthma")));
		}

	}

	@Nested
	class Notes {

		@Test
		void anyClinicianAddsNotesButOnlyTheAuthorChangesThem() throws Exception {
			String noteId = id(addNote(cardiologistSession, "  Patient reports better sleep.  ")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content").value("Patient reports better sleep."))
				.andExpect(jsonPath("$.author.lastName").value("Brown"))
				.andExpect(jsonPath("$.edited").value(false))
				.andExpect(jsonPath("$.editable").value(true)));
			addNote(ownerSession, "Owner follow-up").andExpect(status().isCreated());

			mockMvc.perform(get(RECORD + "/notes", alice.getId()).cookie(ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[*].content").value(contains("Owner follow-up",
						"Patient reports better sleep.")))
				.andExpect(jsonPath("$.content[1].editable").value(false));

			updateNote(ownerSession, noteId, "Changed", 0)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
			mockMvc.perform(delete(RECORD + "/notes/{id}", alice.getId(), noteId).with(spaCsrf()).cookie(ownerSession))
				.andExpect(status().isForbidden());

			updateNote(cardiologistSession, noteId, "Sleep improved.", 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.edited").value(true))
				.andExpect(jsonPath("$.version").value(1));
			mockMvc.perform(delete(RECORD + "/notes/{id}", alice.getId(), noteId).with(spaCsrf())
					.cookie(cardiologistSession))
				.andExpect(status().isNoContent());
			mockMvc.perform(get(RECORD + "/notes", alice.getId()).param("size", "1").cookie(ownerSession))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content", hasSize(1)));
		}

		@Test
		void auditTrailNeverContainsClinicalContent() throws Exception {
			addNote(cardiologistSession, "Confidential observation").andExpect(status().isCreated());
			fixture.flush();
			Integer leaks = jdbc.queryForObject(
					"select count(*) from audit_event where metadata::text like '%Confidential%'", Integer.class);
			assertThat(leaks).isZero();
		}

		@Test
		void rejectsUnsupportedSort() throws Exception {
			mockMvc.perform(get(RECORD + "/notes", alice.getId()).param("sort", "content").cookie(ownerSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SORT"));
		}

	}

	/** Section path, table, not-found code, create body, update body (version placeholder), invalid update. */
	static Stream<Arguments> sections() {
		String tomorrow = LocalDate.now(ZoneId.of("Europe/Paris")).plusDays(2).toString();
		return Stream.of(
				Arguments.of("conditions", "medical_condition", "MEDICAL_CONDITION_NOT_FOUND",
						"{\"name\": \"Asthma\", \"status\": \"ACTIVE\"}",
						"{\"name\": \"Asthma\", \"status\": \"RESOLVED\", \"version\": %d}",
						"{\"name\": \"Asthma\", \"status\": \"ACTIVE\", \"diagnosedOn\": \"%s\", \"version\": 1}"
							.formatted(tomorrow), "diagnosedOn"),
				Arguments.of("medications", "medication", "MEDICATION_NOT_FOUND",
						"{\"name\": \"Metformin\", \"active\": true}",
						"{\"name\": \"Metformin\", \"dosage\": \"500 mg\", \"active\": false, \"version\": %d}",
						"""
						{"name": "Metformin", "active": true, "startDate": "2024-01-01", "endDate": "2023-01-01",
						 "version": 1}""", "endDate"),
				Arguments.of("history", "medical_history_entry", "MEDICAL_HISTORY_ENTRY_NOT_FOUND",
						"{\"description\": \"Appendectomy\"}",
						"{\"description\": \"Appendectomy\", \"occurredOn\": \"2001-05-01\", \"version\": %d}",
						"{\"description\": \"Appendectomy\", \"occurredOn\": \"%s\", \"version\": 1}"
							.formatted(tomorrow), "occurredOn"));
	}

	private Patient patient(Clinic owningClinic, String firstName, Doctor primary) {
		Patient patient = patients.save(new Patient(owningClinic.getId(), firstName, "Martin",
				LocalDate.of(1980, 1, 1), primary.getUserId()));
		records.save(new MedicalRecord(owningClinic.getId(), patient.getId()));
		link(patient, primary, DoctorRelationshipType.PRIMARY);
		return patient;
	}

	private void link(Patient patient, Doctor doctor, DoctorRelationshipType type) {
		patientDoctors.save(new PatientDoctor(patient.getClinicId(), patient.getId(), doctor.getId(), type,
				doctor.getUserId()));
		fixture.flush();
	}

	private int auditCount(String action, UUID entityId) {
		fixture.flush();
		return jdbc.queryForObject("select count(*) from audit_event where action = ? and entity_id = ?",
				Integer.class, action, entityId);
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions addAllergy(Cookie session, UUID patientId, String allergen) throws Exception {
		return send(post(RECORD + "/allergies", patientId), session,
				"{\"allergen\": \"%s\", \"severity\": \"SEVERE\"}".formatted(allergen));
	}

	private ResultActions addCondition(String name, String status, String diagnosedOn) throws Exception {
		String date = diagnosedOn == null ? "null" : "\"" + diagnosedOn + "\"";
		return send(post(RECORD + "/conditions", alice.getId()), ownerSession,
				"{\"name\": \"%s\", \"status\": \"%s\", \"diagnosedOn\": %s}".formatted(name, status, date));
	}

	private ResultActions addMedication(String name, String start, String end, boolean active) throws Exception {
		String endDate = end == null ? "null" : "\"" + end + "\"";
		return send(post(RECORD + "/medications", alice.getId()), cardiologistSession,
				"{\"name\": \"%s\", \"dosage\": \"10 mg\", \"startDate\": \"%s\", \"endDate\": %s, \"active\": %s}"
					.formatted(name, start, endDate, active));
	}

	private void addHistory(String description, String occurredOn) throws Exception {
		String date = occurredOn == null ? "null" : "\"" + occurredOn + "\"";
		send(post(RECORD + "/history", alice.getId()), cardiologistSession,
				"{\"description\": \"%s\", \"occurredOn\": %s}".formatted(description, date))
			.andExpect(status().isCreated());
	}

	private ResultActions addNote(Cookie session, String content) throws Exception {
		return send(post(RECORD + "/notes", alice.getId()), session, "{\"content\": \"%s\"}".formatted(content));
	}

	private ResultActions updateNote(Cookie session, String noteId, String content, long version) throws Exception {
		return send(put(RECORD + "/notes/{id}", alice.getId(), noteId), session,
				"{\"content\": \"%s\", \"version\": %d}".formatted(content, version));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, Cookie session, String body) throws Exception {
		return mockMvc.perform(request.with(spaCsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

}
