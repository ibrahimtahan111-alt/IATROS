package com.iatros.backend.document;

import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.activity.ActivityCategory;
import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.consultation.Consultation;
import com.iatros.backend.consultation.ConsultationRepository;
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

/** Medical documents (roadmap.md Phase 11, api.md §21, decisions.md D-032, D-044). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentApiTests {

	private static final String LIST = "/api/v1/patients/{patientId}/documents";
	private static final String ONE = "/api/v1/documents/{id}";
	private static final String CONTENT = "/api/v1/documents/{id}/content";

	private static final byte[] PDF = "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13 };

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
	private Patient zoe;
	private Cookie ownerSession;
	private Cookie secretarySession;
	private Cookie cardiologistSession;
	private Cookie dermatologistSession;
	private Cookie otherClinicSession;

	@BeforeEach
	void setUp() throws Exception {
		rateLimiter.reset();
		Clinic clinic = fixture.clinic("Document Clinic");
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
		zoe = patient(otherClinic, "Zoe", otherDoctor);

		ownerSession = fixture.login(mockMvc, owner);
		secretarySession = fixture.login(mockMvc, secretary);
		cardiologistSession = fixture.login(mockMvc, daniel);
		dermatologistSession = fixture.login(mockMvc, chloe);
		otherClinicSession = fixture.login(mockMvc, oscar);
	}

	@Nested
	class Uploading {

		@Test
		void aLinkedDoctorUploadsAFileWithItsTypeTakenFromTheContent() throws Exception {
			String id = id(upload(cardiologistSession, alice.getId(), file("..\\..\\scans/ECG report.pdf", PDF,
					"image/png"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.patientId").value(alice.getId().toString()))
				.andExpect(jsonPath("$.fileName").value("ECG report.pdf"))
				.andExpect(jsonPath("$.contentType").value("application/pdf"))
				.andExpect(jsonPath("$.sizeBytes").value(PDF.length))
				.andExpect(jsonPath("$.uploadedBy.lastName").value("Brown"))
				.andExpect(jsonPath("$.archivable").value(true)));

			assertThat(auditCount(AuditActions.DOCUMENT_UPLOADED, UUID.fromString(id))).isEqualTo(1);
			assertThat(auditMetadata(UUID.fromString(id))).contains(alice.getId().toString())
				.doesNotContain("ECG");
			String checksum = jdbc.queryForObject("select checksum_sha256 from document where id = ?", String.class,
					UUID.fromString(id));
			assertThat(checksum).hasSize(64);
		}

		@Test
		void onlyPdfAndImagesWhoseExtensionMatchesTheirContentAreAccepted() throws Exception {
			upload(cardiologistSession, alice.getId(), file("notes.pdf", "plain text".getBytes(), "application/pdf"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("UNSUPPORTED_FILE_TYPE"));
			upload(cardiologistSession, alice.getId(), file("setup.exe", PDF, "application/pdf"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("UNSUPPORTED_FILE_TYPE"));
			upload(cardiologistSession, alice.getId(), file("scan", PDF, "application/pdf"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("UNSUPPORTED_FILE_TYPE"));
			upload(cardiologistSession, alice.getId(), file("x-ray.pdf", PNG, "application/pdf"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("FILE_TYPE_MISMATCH"));
			upload(cardiologistSession, alice.getId(), file("x-ray.PNG", PNG, "image/png"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.contentType").value("image/png"));
		}

		@Test
		void rejectsMissingEmptyAndOversizedFiles() throws Exception {
			mockMvc.perform(multipart(LIST, alice.getId()).with(spaCsrf()).cookie(cardiologistSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("REQUIRED"));
			upload(cardiologistSession, alice.getId(), file("empty.pdf", new byte[0], "application/pdf"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("FILE_EMPTY"));
			byte[] large = Arrays.copyOf(PDF, 20 * 1024 * 1024 + 1);
			upload(cardiologistSession, alice.getId(), file("large.pdf", large, "application/pdf"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("FILE_TOO_LARGE"));
		}

		@Test
		void onlyClinicianslinkedToThePatientCanUpload() throws Exception {
			upload(secretarySession, alice.getId(), file("scan.pdf", PDF, "application/pdf"))
				.andExpect(status().isForbidden());
			upload(dermatologistSession, alice.getId(), file("scan.pdf", PDF, "application/pdf"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
			upload(otherClinicSession, alice.getId(), file("scan.pdf", PDF, "application/pdf"))
				.andExpect(status().isNotFound());
			upload(ownerSession, alice.getId(), file("scan.pdf", PDF, "application/pdf"))
				.andExpect(status().isCreated());
		}

		@Test
		void archivedPatientsReceiveNoDocuments() throws Exception {
			archivePatient(alice);
			upload(cardiologistSession, alice.getId(), file("scan.pdf", PDF, "application/pdf"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"));
		}

		@Test
		void aDocumentCanBelongToOneOfThePatientsConsultations() throws Exception {
			Consultation visit = consultation(alice, cardiologist);
			Consultation brunosVisit = consultation(bruno, dermatologist);

			upload(cardiologistSession, alice.getId(), file("scan.pdf", PDF, "application/pdf"),
					brunosVisit.getId())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("consultationId"));
			upload(cardiologistSession, alice.getId(), file("scan.pdf", PDF, "application/pdf"), visit.getId())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.consultationId").value(visit.getId().toString()));
		}

	}

	@Nested
	class Reading {

		@Test
		void linkedCliniciansListDocumentsNewestFirstWithSearchAndConsultationFilter() throws Exception {
			Consultation visit = consultation(alice, cardiologist);
			upload(cardiologistSession, alice.getId(), file("blood test.pdf", PDF, "application/pdf"));
			upload(cardiologistSession, alice.getId(), file("ECG.png", PNG, "image/png"), visit.getId());
			link(alice, dermatologist);

			mockMvc.perform(get(LIST, alice.getId()).cookie(dermatologistSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].fileName").value(contains("ECG.png", "blood test.pdf")))
				.andExpect(jsonPath("$.content[0].archivable").value(false));
			mockMvc.perform(get(LIST, alice.getId()).param("search", "BLOOD").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].fileName").value(contains("blood test.pdf")))
				.andExpect(jsonPath("$.content[0].archivable").value(true));
			mockMvc.perform(get(LIST, alice.getId()).param("consultationId", visit.getId().toString())
				.cookie(cardiologistSession))
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.totalElements").value(1));
			mockMvc.perform(get(LIST, alice.getId()).param("sort", "fileName,asc").cookie(cardiologistSession))
				.andExpect(jsonPath("$.content[*].fileName").value(contains("blood test.pdf", "ECG.png")));
		}

		@Test
		void secretariesUnlinkedDoctorsAndOtherClinicsSeeNoDocuments() throws Exception {
			String id = id(upload(cardiologistSession, alice.getId(), file("scan.pdf", PDF, "application/pdf")));

			mockMvc.perform(get(LIST, alice.getId()).cookie(secretarySession)).andExpect(status().isForbidden());
			mockMvc.perform(get(LIST, alice.getId()).cookie(dermatologistSession)).andExpect(status().isNotFound());
			mockMvc.perform(get(ONE, id).cookie(secretarySession)).andExpect(status().isForbidden());
			mockMvc.perform(get(ONE, id).cookie(dermatologistSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
			mockMvc.perform(get(ONE, id).cookie(otherClinicSession)).andExpect(status().isNotFound());
			mockMvc.perform(get(CONTENT, id).cookie(secretarySession)).andExpect(status().isForbidden());
			mockMvc.perform(get(CONTENT, id).cookie(dermatologistSession)).andExpect(status().isNotFound());
			mockMvc.perform(get(CONTENT, id).cookie(otherClinicSession)).andExpect(status().isNotFound());
			assertThat(auditCount(AuditActions.DOCUMENT_VIEWED, UUID.fromString(id))).isZero();
		}

		@Test
		void otherClinicsDocumentsAreInvisible() throws Exception {
			String id = id(upload(otherClinicSession, zoe.getId(), file("scan.pdf", PDF, "application/pdf")));
			mockMvc.perform(get(ONE, id).cookie(ownerSession)).andExpect(status().isNotFound());
			mockMvc.perform(get(CONTENT, id).cookie(ownerSession)).andExpect(status().isNotFound());
			mockMvc.perform(delete(ONE, id).with(spaCsrf()).cookie(ownerSession)).andExpect(status().isNotFound());
		}

		@Test
		void theFileIsServedWithItsVerifiedTypeAndEveryAccessIsAudited() throws Exception {
			String id = id(upload(cardiologistSession, alice.getId(), file("ECG report.pdf", PDF,
					"application/pdf")));

			mockMvc.perform(get(CONTENT, id).cookie(cardiologistSession))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf"))
				.andExpect(content().bytes(PDF))
				.andExpect(header().string("Content-Disposition", containsString("inline")))
				.andExpect(header().string("Content-Disposition", containsString("ECG")))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Cache-Control", containsString("no-store")));
			mockMvc.perform(get(CONTENT, id).param("download", "true").cookie(ownerSession))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("attachment")));

			assertThat(auditCount(AuditActions.DOCUMENT_VIEWED, UUID.fromString(id))).isEqualTo(2);
			assertThat(ActivityCategory.of(AuditActions.DOCUMENT_VIEWED)).isEmpty();
		}

		@Test
		void archivedPatientsDocumentsRemainReadable() throws Exception {
			String id = id(upload(cardiologistSession, alice.getId(), file("scan.pdf", PDF, "application/pdf")));
			archivePatient(alice);
			mockMvc.perform(get(LIST, alice.getId()).cookie(cardiologistSession))
				.andExpect(jsonPath("$.content", hasSize(1)));
			mockMvc.perform(get(CONTENT, id).cookie(cardiologistSession)).andExpect(status().isOk());
		}

	}

	@Nested
	class Archiving {

		@Test
		void theUploaderArchivesAndTheDocumentDisappearsButIsKept() throws Exception {
			String id = id(upload(cardiologistSession, alice.getId(), file("scan.pdf", PDF, "application/pdf")));

			mockMvc.perform(delete(ONE, id).with(spaCsrf()).cookie(cardiologistSession))
				.andExpect(status().isNoContent());

			mockMvc.perform(get(ONE, id).cookie(cardiologistSession)).andExpect(status().isNotFound());
			mockMvc.perform(get(CONTENT, id).cookie(cardiologistSession)).andExpect(status().isNotFound());
			mockMvc.perform(get(LIST, alice.getId()).cookie(cardiologistSession))
				.andExpect(jsonPath("$.content", hasSize(0)));
			assertThat(jdbc.queryForObject("select count(*) from document where id = ? and archived_at is not null",
					Integer.class, UUID.fromString(id))).isEqualTo(1);
			assertThat(auditCount(AuditActions.DOCUMENT_ARCHIVED, UUID.fromString(id))).isEqualTo(1);
		}

		@Test
		void otherDoctorsCannotArchiveButTheOwnerCan() throws Exception {
			String id = id(upload(cardiologistSession, alice.getId(), file("scan.pdf", PDF, "application/pdf")));
			link(alice, dermatologist);

			mockMvc.perform(delete(ONE, id).with(spaCsrf()).cookie(dermatologistSession))
				.andExpect(status().isForbidden());
			mockMvc.perform(delete(ONE, id).with(spaCsrf()).cookie(secretarySession))
				.andExpect(status().isForbidden());
			mockMvc.perform(delete(ONE, id).with(spaCsrf()).cookie(ownerSession))
				.andExpect(status().isNoContent());
		}

		@Test
		void archivedPatientsDocumentsCannotBeArchived() throws Exception {
			String id = id(upload(cardiologistSession, alice.getId(), file("scan.pdf", PDF, "application/pdf")));
			archivePatient(alice);
			mockMvc.perform(delete(ONE, id).with(spaCsrf()).cookie(cardiologistSession))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"));
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

	private Consultation consultation(Patient patient, Doctor doctor) {
		MedicalRecord record = records.findByPatientIdAndClinicId(patient.getId(), patient.getClinicId())
			.orElseThrow();
		Consultation consultation = consultations.save(new Consultation(patient.getClinicId(), record.getId(),
				doctor.getId(), null, Instant.now().minus(1, ChronoUnit.DAYS), doctor.getUserId()));
		consultation.updateClinicalContent(consultation.getConsultedAt(), "Check-up", null, null, null, null, null,
				doctor.getUserId());
		fixture.flush();
		return consultation;
	}

	private void link(Patient patient, Doctor doctor) {
		patientDoctors.save(new PatientDoctor(patient.getClinicId(), patient.getId(), doctor.getId(),
				DoctorRelationshipType.AUTHORIZED, owner.getId()));
		fixture.flush();
	}

	private void archivePatient(Patient patient) {
		patient.archive(Instant.now(), owner.getId());
		fixture.flush();
	}

	private int auditCount(String action, UUID entityId) {
		fixture.flush();
		return jdbc.queryForObject("select count(*) from audit_event where action = ? and entity_id = ?",
				Integer.class, action, entityId);
	}

	private String auditMetadata(UUID entityId) {
		return jdbc.queryForObject("select metadata::text from audit_event where entity_id = ?", String.class,
				entityId);
	}

	private static MockMultipartFile file(String name, byte[] bytes, String declaredType) {
		return new MockMultipartFile("file", name, declaredType, bytes);
	}

	private ResultActions upload(Cookie session, UUID patientId, MockMultipartFile file) throws Exception {
		return upload(session, patientId, file, null);
	}

	private ResultActions upload(Cookie session, UUID patientId, MockMultipartFile file, UUID consultationId)
			throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(LIST, patientId).file(file);
		if (consultationId != null) {
			request.param("consultationId", consultationId.toString());
		}
		return mockMvc.perform(request.with(spaCsrf()).cookie(session));
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

}
