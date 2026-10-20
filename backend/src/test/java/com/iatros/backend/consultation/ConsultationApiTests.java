package com.iatros.backend.consultation;

import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.medicalrecord.MedicalRecord;
import com.iatros.backend.medicalrecord.MedicalRecordRepository;
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

/** Consultations (roadmap.md Phase 8, api.md §16, decisions.md D-037). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsultationApiTests {

	private static final String LIST = "/api/v1/patients/{patientId}/consultations";
	private static final String ONE = "/api/v1/consultations/{id}";

	@Autowired private MockMvc mockMvc;
	@Autowired private TestClinicFixture fixture;
	@Autowired private RateLimiter rateLimiter;
	@Autowired private PatientRepository patients;
	@Autowired private PatientDoctorRepository patientDoctors;
	@Autowired private MedicalRecordRepository records;
	@Autowired private ConsultationRepository consultations;
	@Autowired private JdbcTemplate jdbc;

	private AppUser owner;
	private Doctor cardiologist;
	private Doctor dermatologist;
	private Patient alice;
	private Patient bruno;
	private Consultation otherClinicConsultation;
	private Cookie ownerSession;
	private Cookie secretarySession;
	private Cookie cardiologistSession;
	private Cookie dermatologistSession;

	@BeforeEach
	void setUp() throws Exception {
		rateLimiter.reset();
		Clinic clinic = fixture.clinic("Consultation Clinic");
		owner = fixture.activeUser(clinic, Role.OWNER, "Olivia", "Owner");
		AppUser secretary = fixture.activeUser(clinic, Role.SECRETARY, "Sara", "Adams");
		AppUser daniel = fixture.activeUser(clinic, Role.DOCTOR, "Daniel", "Brown");
		AppUser chloe = fixture.activeUser(clinic, Role.DOCTOR, "Chloe", "Carter");
		cardiologist = fixture.doctorProfile(clinic, daniel, "CARDIOLOGY");
		dermatologist = fixture.doctorProfile(clinic, chloe, "DERMATOLOGY");
		alice = patient(clinic, "Alice", cardiologist);
		bruno = patient(clinic, "Bruno", dermatologist);

		Clinic otherClinic = fixture.clinic("Other Clinic");
		AppUser oscar = fixture.activeUser(otherClinic, Role.DOCTOR, "Oscar", "Abbott");
		Doctor otherDoctor = fixture.doctorProfile(otherClinic, oscar, "CARDIOLOGY");
		Patient zoe = patient(otherClinic, "Zoe", otherDoctor);
		MedicalRecord zoeRecord = records.findByPatientIdAndClinicId(zoe.getId(), otherClinic.getId()).orElseThrow();
		otherClinicConsultation = consultations.save(new Consultation(otherClinic.getId(), zoeRecord.getId(),
				otherDoctor.getId(), null, Instant.now().minus(1, ChronoUnit.DAYS), oscar.getId()));
		otherClinicConsultation.updateClinicalContent(otherClinicConsultation.getConsultedAt(), "Check-up", null,
				null, null, null, null, oscar.getId());

		ownerSession = fixture.login(mockMvc, owner);
		secretarySession = fixture.login(mockMvc, secretary);
		cardiologistSession = fixture.login(mockMvc, daniel);
		dermatologistSession = fixture.login(mockMvc, chloe);
	}

	@Nested
	class Recording {

		@Test
		void aDoctorRecordsAConsultationAsThemselves() throws Exception {
			create(cardiologistSession, alice.getId(), hoursAgo(2), "  Chest pain  ", null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.patientId").value(alice.getId().toString()))
				.andExpect(jsonPath("$.doctor.id").value(cardiologist.getId().toString()))
				.andExpect(jsonPath("$.doctor.lastName").value("Brown"))
				.andExpect(jsonPath("$.reason").value("Chest pain"))
				.andExpect(jsonPath("$.editable").value(true))
				.andExpect(jsonPath("$.version").value(0));
		}

		@Test
		void ownersWithoutADoctorProfileCannotRecord() throws Exception {
			create(ownerSession, alice.getId(), hoursAgo(2), "Chest pain", null)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}

		@Test
		void secretariesAndUnlinkedDoctorsCannotRecord() throws Exception {
			create(secretarySession, alice.getId(), hoursAgo(2), "Chest pain", null)
				.andExpect(status().isForbidden());
			create(dermatologistSession, alice.getId(), hoursAgo(2), "Chest pain", null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
		}

		@Test
		void validatesDatesAndRequiredFields() throws Exception {
			create(cardiologistSession, alice.getId(), Instant.now().plus(1, ChronoUnit.DAYS), "Chest pain", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'consultedAt')].code")
					.value(contains("DATE_IN_FUTURE")));
			create(cardiologistSession, alice.getId(), Instant.parse("2026-01-10T10:00:00Z"), "Chest pain",
					LocalDate.parse("2026-01-09"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'followUpOn')].code")
					.value(contains("BEFORE_CONSULTATION")));
			create(cardiologistSession, alice.getId(), hoursAgo(2), " ", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
		}

		@Test
		void archivedPatientsCannotReceiveConsultations() throws Exception {
			archive(alice);
			create(cardiologistSession, alice.getId(), hoursAgo(2), "Chest pain", null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"));
		}

	}

	@Nested
	class Reading {

		@Test
		void everyLinkedClinicianSeesThePatientsConsultations() throws Exception {
			String id = id(create(cardiologistSession, alice.getId(), hoursAgo(2), "Chest pain", null));
			link(alice, dermatologist);

			for (Cookie session : new Cookie[] { ownerSession, dermatologistSession }) {
				mockMvc.perform(get(LIST, alice.getId()).cookie(session))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[*].id").value(contains(id)))
					.andExpect(jsonPath("$.content[0].doctor.lastName").value("Brown"));
				mockMvc.perform(get(ONE, id).cookie(session))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.reason").value("Chest pain"))
					.andExpect(jsonPath("$.editable").value(false));
			}
		}

		@Test
		void secretariesNeverReadConsultations() throws Exception {
			String id = id(create(cardiologistSession, alice.getId(), hoursAgo(2), "Chest pain", null));
			mockMvc.perform(get(LIST, alice.getId()).cookie(secretarySession)).andExpect(status().isForbidden());
			mockMvc.perform(get(ONE, id).cookie(secretarySession)).andExpect(status().isForbidden());
		}

		@Test
		void unlinkedDoctorsAndOtherClinicsGetNotFound() throws Exception {
			String id = id(create(cardiologistSession, alice.getId(), hoursAgo(2), "Chest pain", null));
			mockMvc.perform(get(ONE, id).cookie(dermatologistSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CONSULTATION_NOT_FOUND"));
			mockMvc.perform(get(LIST, alice.getId()).cookie(dermatologistSession))
				.andExpect(status().isNotFound());
			mockMvc.perform(get(ONE, otherClinicConsultation.getId()).cookie(ownerSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CONSULTATION_NOT_FOUND"));
		}

		@Test
		void listsNewestFirstWithDoctorAndDateFilters() throws Exception {
			link(alice, dermatologist);
			create(cardiologistSession, alice.getId(), Instant.parse("2026-03-01T09:00:00Z"), "Older", null);
			create(dermatologistSession, alice.getId(), Instant.parse("2026-05-01T09:00:00Z"), "Rash", null);
			create(cardiologistSession, alice.getId(), Instant.parse("2026-06-01T09:00:00Z"), "Newer", null);

			mockMvc.perform(get(LIST, alice.getId()).cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].reason").value(contains("Newer", "Rash", "Older")));
			mockMvc.perform(get(LIST, alice.getId()).param("doctorId", cardiologist.getId().toString())
					.cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].reason").value(contains("Newer", "Older")));
			mockMvc.perform(get(LIST, alice.getId()).param("from", "2026-05-01").param("to", "2026-05-31")
					.cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].reason").value(contains("Rash")));
			mockMvc.perform(get(LIST, alice.getId()).param("from", "2026-05-02").param("to", "2026-05-01")
					.cookie(ownerSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'to')].code").value(contains("END_BEFORE_START")));
			mockMvc.perform(get(LIST, alice.getId()).param("size", "2").cookie(ownerSession))
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalElements").value(3));
		}

	}

	@Nested
	class Editing {

		@Test
		void onlyTheConsultationsDoctorEditsIt() throws Exception {
			String id = id(create(cardiologistSession, alice.getId(), hoursAgo(2), "Chest pain", null));
			link(alice, dermatologist);

			update(ownerSession, id, "Angina", 0).andExpect(status().isForbidden());
			update(dermatologistSession, id, "Angina", 0).andExpect(status().isForbidden());
			update(secretarySession, id, "Angina", 0).andExpect(status().isForbidden());

			update(cardiologistSession, id, "Angina", 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.diagnosis").value("Angina"))
				.andExpect(jsonPath("$.version").value(1));
			update(cardiologistSession, id, "Stable angina", 0)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

			fixture.flush();
			String metadata = jdbc.queryForObject(
					"select metadata::text from audit_event where action = ? and entity_id = ?", String.class,
					AuditActions.CONSULTATION_UPDATED, UUID.fromString(id));
			assertThat(metadata).contains("diagnosis").doesNotContain("Angina");
			assertThat(auditCount(AuditActions.CONSULTATION_CREATED, UUID.fromString(id))).isEqualTo(1);
		}

		@Test
		void archivedPatientsConsultationsAreReadOnly() throws Exception {
			String id = id(create(cardiologistSession, alice.getId(), hoursAgo(2), "Chest pain", null));
			archive(alice);
			mockMvc.perform(get(ONE, id).cookie(cardiologistSession)).andExpect(status().isOk());
			update(cardiologistSession, id, "Angina", 0)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"));
		}

		@Test
		void otherClinicsConsultationsCannotBeEdited() throws Exception {
			update(ownerSession, otherClinicConsultation.getId().toString(), "Angina", 0)
				.andExpect(status().isNotFound());
		}

	}

	private Patient patient(Clinic owningClinic, String firstName, Doctor primary) {
		Patient patient = patients.save(new Patient(owningClinic.getId(), firstName, "Martin",
				LocalDate.of(1980, 1, 1), primary.getUserId()));
		records.save(new MedicalRecord(owningClinic.getId(), patient.getId()));
		patientDoctors.save(new PatientDoctor(owningClinic.getId(), patient.getId(), primary.getId(),
				DoctorRelationshipType.PRIMARY, primary.getUserId()));
		fixture.flush();
		return patient;
	}

	private void link(Patient patient, Doctor doctor) {
		patientDoctors.save(new PatientDoctor(patient.getClinicId(), patient.getId(), doctor.getId(),
				DoctorRelationshipType.AUTHORIZED, owner.getId()));
		fixture.flush();
	}

	private void archive(Patient patient) {
		patient.archive(Instant.now(), owner.getId());
		fixture.flush();
	}

	private int auditCount(String action, UUID entityId) {
		fixture.flush();
		return jdbc.queryForObject("select count(*) from audit_event where action = ? and entity_id = ?",
				Integer.class, action, entityId);
	}

	private static Instant hoursAgo(int hours) {
		return Instant.now().minus(hours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions create(Cookie session, UUID patientId, Instant consultedAt, String reason,
			LocalDate followUpOn) throws Exception {
		String followUp = followUpOn == null ? "null" : "\"" + followUpOn + "\"";
		String body = """
				{"consultedAt": "%s", "reason": "%s", "clinicalNotes": "Examined.", "followUpOn": %s}"""
			.formatted(consultedAt, reason, followUp);
		return mockMvc.perform(post(LIST, patientId).with(spaCsrf()).cookie(session)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions update(Cookie session, String consultationId, String diagnosis, long version)
			throws Exception {
		String body = """
				{"consultedAt": "%s", "reason": "Chest pain", "clinicalNotes": "Examined.", "diagnosis": "%s",
				 "version": %d}"""
			.formatted(hoursAgo(1), diagnosis, version);
		return mockMvc.perform(put(ONE, consultationId).with(spaCsrf()).cookie(session)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

}
