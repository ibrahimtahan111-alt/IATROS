package com.iatros.backend.dashboard;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.appointment.Appointment;
import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.AppointmentType;
import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
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

import jakarta.servlet.http.Cookie;

/** Dashboards, KPIs, and clinic activity (roadmap.md Phase 10, api.md §22–24, decisions.md D-031, D-039). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardApiTests {

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

	@Autowired private MockMvc mockMvc;
	@Autowired private TestClinicFixture fixture;
	@Autowired private RateLimiter rateLimiter;
	@Autowired private PatientRepository patients;
	@Autowired private PatientDoctorRepository patientDoctors;
	@Autowired private MedicalRecordRepository records;
	@Autowired private AppointmentRepository appointments;
	@Autowired private AuditService audit;
	@Autowired private JdbcTemplate jdbc;

	private Clinic clinic;
	private AppUser owner;
	private AppUser secretary;
	private AppUser daniel;
	private AppUser chloe;
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
	private LocalDate today;

	@BeforeEach
	void setUp() throws Exception {
		rateLimiter.reset();
		clinic = fixture.clinic("Dashboard Clinic");
		owner = fixture.activeUser(clinic, Role.OWNER, "Olivia", "Owner");
		secretary = fixture.activeUser(clinic, Role.SECRETARY, "Sara", "Adams");
		daniel = fixture.activeUser(clinic, Role.DOCTOR, "Daniel", "Brown");
		chloe = fixture.activeUser(clinic, Role.DOCTOR, "Chloe", "Carter");
		cardiologist = fixture.doctorProfile(clinic, daniel, "CARDIOLOGY");
		dermatologist = fixture.doctorProfile(clinic, chloe, "DERMATOLOGY");
		alice = patient(clinic, "Alice", cardiologist);
		bruno = patient(clinic, "Bruno", dermatologist);

		Clinic otherClinic = fixture.clinic("Other Clinic");
		AppUser oscar = fixture.activeUser(otherClinic, Role.DOCTOR, "Oscar", "Abbott");
		otherClinicDoctor = fixture.doctorProfile(otherClinic, oscar, "CARDIOLOGY");
		otherClinicPatient = patient(otherClinic, "Zoe", otherClinicDoctor);

		today = LocalDate.now(PARIS);
		ownerSession = fixture.login(mockMvc, owner);
		secretarySession = fixture.login(mockMvc, secretary);
		cardiologistSession = fixture.login(mockMvc, daniel);
		dermatologistSession = fixture.login(mockMvc, chloe);
	}

	@Nested
	class Dashboards {

		@BeforeEach
		void today() {
			appointment(alice, cardiologist, today, "09:00", AppointmentStatus.COMPLETED);
			appointment(bruno, dermatologist, today, "10:00", AppointmentStatus.WAITING);
			appointment(alice, cardiologist, today, "11:00", AppointmentStatus.SCHEDULED);
			appointment(bruno, dermatologist, today, "12:00", AppointmentStatus.CANCELLED);
			appointment(alice, cardiologist, today.plusDays(2), "09:00", AppointmentStatus.CONFIRMED);
			appointment(otherClinicPatient, otherClinicDoctor, today, "09:00", AppointmentStatus.SCHEDULED);
		}

		@Test
		void theOwnerDashboardSummarizesTheClinicsDay() throws Exception {
			mockMvc.perform(get("/api/v1/dashboard/owner").cookie(ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.today").value(today.toString()))
				.andExpect(jsonPath("$.timeZone").value("Europe/Paris"))
				.andExpect(jsonPath("$.summary.appointments").value(3))
				.andExpect(jsonPath("$.summary.patients").value(2))
				.andExpect(jsonPath("$.summary.remaining").value(1))
				.andExpect(jsonPath("$.summary.waiting").value(1))
				.andExpect(jsonPath("$.summary.completed").value(1))
				.andExpect(jsonPath("$.summary.cancelled").value(1))
				.andExpect(jsonPath("$.activePatients").value(2))
				.andExpect(jsonPath("$.activeDoctors").value(2))
				.andExpect(jsonPath("$.schedule", hasSize(3)))
				.andExpect(jsonPath("$.schedule[*].alerts", everyItem(nullValue())))
				.andExpect(jsonPath("$.scheduleTruncated").value(false))
				.andExpect(jsonPath("$.doctors[*].doctor.lastName").value(contains("Brown", "Carter")))
				.andExpect(jsonPath("$.doctors[0].appointments").value(2))
				.andExpect(jsonPath("$.doctors[0].completed").value(1))
				.andExpect(jsonPath("$.doctors[1].inProgress").value(1));
		}

		@Test
		void theDoctorDashboardCoversOnlyTheDoctorsOwnDay() throws Exception {
			allergy(alice, "Penicillin", "SEVERE");
			allergy(alice, "Pollen", "MILD");
			condition(alice, "Hypertension", "ACTIVE");
			mockMvc.perform(get("/api/v1/dashboard/doctor").cookie(cardiologistSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.doctor.lastName").value("Brown"))
				.andExpect(jsonPath("$.summary.appointments").value(2))
				.andExpect(jsonPath("$.summary.patients").value(1))
				.andExpect(jsonPath("$.upcomingAppointments").value(1))
				.andExpect(jsonPath("$.patients").value(1))
				.andExpect(jsonPath("$.primaryPatients").value(1))
				.andExpect(jsonPath("$.schedule[*].doctor.lastName").value(everyItem(is("Brown"))))
				.andExpect(jsonPath("$.schedule[0].alerts.severeAllergies").value(contains("Penicillin")))
				.andExpect(jsonPath("$.schedule[0].alerts.activeConditions").value(contains("Hypertension")))
				.andExpect(jsonPath("$.recentPatients[*].patient.firstName").value(contains("Alice")));
		}

		@Test
		void clinicalAlertsAreOmittedForPatientsTheDoctorIsNotAuthorizedFor() throws Exception {
			allergy(bruno, "Latex", "SEVERE");
			appointment(bruno, cardiologist, today, "15:00", AppointmentStatus.SCHEDULED);
			mockMvc.perform(get("/api/v1/dashboard/doctor").cookie(cardiologistSession))
				.andExpect(jsonPath("$.schedule[?(@.patient.firstName == 'Bruno')].alerts").value(contains(nullValue())))
				.andExpect(jsonPath("$.recentPatients", hasSize(1)));
		}

		@Test
		void secretariesHaveNoDashboardAndDoctorsNoOwnerDashboard() throws Exception {
			mockMvc.perform(get("/api/v1/dashboard/owner").cookie(secretarySession))
				.andExpect(status().isForbidden());
			mockMvc.perform(get("/api/v1/dashboard/doctor").cookie(secretarySession))
				.andExpect(status().isForbidden());
			mockMvc.perform(get("/api/v1/dashboard/owner").cookie(cardiologistSession))
				.andExpect(status().isForbidden());
		}

		@Test
		void ownersWithoutADoctorProfileHaveNoDoctorDashboard() throws Exception {
			mockMvc.perform(get("/api/v1/dashboard/doctor").cookie(ownerSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}

		@Test
		void dashboardsRequireASession() throws Exception {
			mockMvc.perform(get("/api/v1/dashboard/owner")).andExpect(status().isUnauthorized());
		}

	}

	@Nested
	class Kpis {

		private LocalDate from;
		private LocalDate to;

		@BeforeEach
		void lastWeek() {
			from = today.minusDays(7);
			to = today.minusDays(1);
			appointment(alice, cardiologist, today.minusDays(5), "09:00", AppointmentStatus.COMPLETED);
			appointment(alice, cardiologist, today.minusDays(4), "09:00", AppointmentStatus.COMPLETED);
			appointment(alice, cardiologist, today.minusDays(3), "09:00", AppointmentStatus.NO_SHOW);
			appointment(alice, cardiologist, today.minusDays(2), "09:00", AppointmentStatus.CANCELLED);
			appointment(bruno, dermatologist, today.minusDays(2), "10:00", AppointmentStatus.COMPLETED);
			appointment(bruno, dermatologist, today.minusDays(10), "10:00", AppointmentStatus.COMPLETED);
			appointment(otherClinicPatient, otherClinicDoctor, today.minusDays(3), "09:00",
					AppointmentStatus.COMPLETED);
			consultation(alice, cardiologist, today.minusDays(5));
		}

		@Test
		void clinicKpisFollowTheDocumentedDefinitions() throws Exception {
			mockMvc.perform(kpis("/api/v1/kpis/clinic", ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.from").value(from.toString()))
				.andExpect(jsonPath("$.previousTo").value(from.minusDays(1).toString()))
				.andExpect(jsonPath("$.previousFrom").value(from.minusDays(7).toString()))
				.andExpect(jsonPath("$.current.appointments").value(5))
				.andExpect(jsonPath("$.current.appointmentsByStatus.COMPLETED").value(3))
				.andExpect(jsonPath("$.current.appointmentsByStatus.SCHEDULED").value(0))
				.andExpect(jsonPath("$.current.completionRate").value(closeTo(0.6, 0.0001)))
				.andExpect(jsonPath("$.current.noShowRate").value(closeTo(0.25, 0.0001)))
				.andExpect(jsonPath("$.current.cancellationRate").value(closeTo(0.2, 0.0001)))
				.andExpect(jsonPath("$.current.activePatients").value(2))
				.andExpect(jsonPath("$.current.consultations").value(1))
				.andExpect(jsonPath("$.previous.appointments").value(1))
				.andExpect(jsonPath("$.trendBucket").value("DAY"))
				.andExpect(jsonPath("$.trend", hasSize(7)))
				.andExpect(jsonPath("$.doctors[*].doctor.lastName").value(contains("Brown", "Carter")))
				.andExpect(jsonPath("$.doctors[0].appointments").value(4))
				.andExpect(jsonPath("$.doctors[0].consultations").value(1))
				.andExpect(jsonPath("$.doctors[1].completionRate").value(closeTo(1.0, 0.0001)));
		}

		@Test
		void ratesAreNullWithoutData() throws Exception {
			mockMvc.perform(get("/api/v1/kpis/clinic").param("from", today.minusYears(2).toString())
				.param("to", today.minusYears(2).plusDays(3).toString())
				.cookie(ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.current.appointments").value(0))
				.andExpect(jsonPath("$.current.completionRate").value(nullValue()));
		}

		@Test
		void newPatientsCountPatientsCreatedInThePeriod() throws Exception {
			mockMvc.perform(get("/api/v1/kpis/clinic").cookie(ownerSession))
				.andExpect(jsonPath("$.to").value(today.toString()))
				.andExpect(jsonPath("$.current.newPatients").value(2));
			mockMvc.perform(get("/api/v1/kpis/doctors/{id}", cardiologist.getId()).cookie(cardiologistSession))
				.andExpect(jsonPath("$.current.newPatients").value(1));
		}

		@Test
		void doctorsSeeOnlyTheirOwnKpis() throws Exception {
			mockMvc.perform(kpis("/api/v1/kpis/doctors/" + cardiologist.getId(), cardiologistSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.doctor.lastName").value("Brown"))
				.andExpect(jsonPath("$.current.appointments").value(4))
				.andExpect(jsonPath("$.current.activePatients").value(1))
				.andExpect(jsonPath("$.doctors", hasSize(0)));
			mockMvc.perform(kpis("/api/v1/kpis/doctors/" + dermatologist.getId(), cardiologistSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DOCTOR_NOT_FOUND"));
			mockMvc.perform(kpis("/api/v1/kpis/clinic", cardiologistSession))
				.andExpect(status().isForbidden());
		}

		@Test
		void ownersSeeAnyClinicDoctorsKpisButNotOtherClinics() throws Exception {
			mockMvc.perform(kpis("/api/v1/kpis/doctors/" + dermatologist.getId(), ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.current.appointments").value(1));
			mockMvc.perform(kpis("/api/v1/kpis/doctors/" + otherClinicDoctor.getId(), ownerSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DOCTOR_NOT_FOUND"));
		}

		@Test
		void secretariesHaveNoKpis() throws Exception {
			mockMvc.perform(kpis("/api/v1/kpis/clinic", secretarySession)).andExpect(status().isForbidden());
			mockMvc.perform(kpis("/api/v1/kpis/doctors/" + cardiologist.getId(), secretarySession))
				.andExpect(status().isForbidden());
		}

		@Test
		void validatesThePeriod() throws Exception {
			mockMvc.perform(get("/api/v1/kpis/clinic").param("from", today.toString())
				.param("to", today.minusDays(1).toString())
				.cookie(ownerSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("END_BEFORE_START"));
			mockMvc.perform(get("/api/v1/kpis/clinic").param("from", today.minusDays(400).toString())
				.param("to", today.toString())
				.cookie(ownerSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("OUT_OF_RANGE"));
		}

		@Test
		void longPeriodsAreChartedByMonth() throws Exception {
			mockMvc.perform(get("/api/v1/kpis/clinic").param("from", today.minusDays(89).toString())
				.param("to", today.toString())
				.cookie(ownerSession))
				.andExpect(jsonPath("$.trendBucket").value("MONTH"))
				.andExpect(jsonPath("$.trend[0].date").value(today.minusDays(89).withDayOfMonth(1).toString()));
		}

		private org.springframework.test.web.servlet.RequestBuilder kpis(String path, Cookie session) {
			return get(path).param("from", from.toString()).param("to", to.toString()).cookie(session);
		}

	}

	@Nested
	class Activity {

		@BeforeEach
		void events() {
			UUID appointmentId = appointment(alice, cardiologist, today.plusDays(1), "09:00",
					AppointmentStatus.SCHEDULED).getId();
			audit.record(clinic.getId(), secretary.getId(), AuditActions.APPOINTMENT_CREATED,
					AuditActions.ENTITY_APPOINTMENT, appointmentId,
					Map.of("patientId", alice.getId().toString(), "doctorId", cardiologist.getId().toString()));
			audit.record(clinic.getId(), chloe.getId(), AuditActions.MEDICAL_ENTRY_CREATED, AuditActions.ENTITY_ALLERGY,
					UUID.randomUUID(), Map.of("patientId", bruno.getId().toString()));
			audit.record(clinic.getId(), owner.getId(), AuditActions.SCHEDULE_UPDATED, AuditActions.ENTITY_DOCTOR,
					dermatologist.getId(), Map.of("periods", 5));
			audit.record(clinic.getId(), owner.getId(), AuditActions.LOGIN_SUCCEEDED, AuditActions.ENTITY_USER,
					owner.getId());
			audit.record(otherClinicDoctor.getClinicId(), otherClinicDoctor.getUserId(),
					AuditActions.PATIENT_CREATED, AuditActions.ENTITY_PATIENT, otherClinicPatient.getId());
			fixture.flush();
		}

		@Test
		void ownersSeeTheClinicsActivityWithoutSecurityEvents() throws Exception {
			mockMvc.perform(get("/api/v1/activity").cookie(ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[*].action").value(containsInAnyOrder("APPOINTMENT_CREATED",
						"MEDICAL_ENTRY_CREATED", "SCHEDULE_UPDATED")))
				.andExpect(jsonPath("$.content[?(@.action == 'APPOINTMENT_CREATED')].actor.lastName")
					.value(contains("Adams")))
				.andExpect(jsonPath("$.content[?(@.action == 'APPOINTMENT_CREATED')].patient.firstName")
					.value(contains("Alice")))
				.andExpect(jsonPath("$.content[?(@.action == 'APPOINTMENT_CREATED')].category")
					.value(contains("APPOINTMENTS")))
				.andExpect(jsonPath("$.content[?(@.action == 'SCHEDULE_UPDATED')].doctor.lastName")
					.value(contains("Carter")))
				.andExpect(jsonPath("$.content[?(@.action == 'MEDICAL_ENTRY_CREATED')].entityType")
					.value(contains("ALLERGY")));
		}

		@Test
		void ownersFilterByTypeAndDoctor() throws Exception {
			mockMvc.perform(get("/api/v1/activity").param("type", "CLINICAL").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].action").value(contains("MEDICAL_ENTRY_CREATED")));
			mockMvc.perform(get("/api/v1/activity").param("doctorId", dermatologist.getId().toString())
				.cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].action")
					.value(containsInAnyOrder("MEDICAL_ENTRY_CREATED", "SCHEDULE_UPDATED")));
			mockMvc.perform(get("/api/v1/activity").param("from", today.plusDays(1).toString()).cookie(ownerSession))
				.andExpect(jsonPath("$.totalElements").value(0));
		}

		@Test
		void doctorsSeeOnlyTheirOwnActivity() throws Exception {
			mockMvc.perform(get("/api/v1/activity").cookie(cardiologistSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].action").value(contains("APPOINTMENT_CREATED")))
				.andExpect(jsonPath("$.content[0].patient.firstName").value("Alice"));
			mockMvc.perform(get("/api/v1/activity").cookie(dermatologistSession))
				.andExpect(jsonPath("$.content[*].action")
					.value(containsInAnyOrder("MEDICAL_ENTRY_CREATED", "SCHEDULE_UPDATED")));
			mockMvc.perform(get("/api/v1/activity").param("doctorId", dermatologist.getId().toString())
				.cookie(cardiologistSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DOCTOR_NOT_FOUND"));
		}

		@Test
		void patientNamesAreHiddenFromDoctorsWhoAreNotAuthorized() throws Exception {
			audit.record(clinic.getId(), daniel.getId(), AuditActions.MEDICAL_ENTRY_CREATED,
					AuditActions.ENTITY_ALLERGY, UUID.randomUUID(), Map.of("patientId", bruno.getId().toString()));
			fixture.flush();
			mockMvc.perform(get("/api/v1/activity").param("type", "CLINICAL").cookie(cardiologistSession))
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].patient").value(nullValue()));
		}

		@Test
		void statusChangesCarryTheNewStatus() throws Exception {
			audit.record(clinic.getId(), daniel.getId(), AuditActions.APPOINTMENT_STATUS_CHANGED,
					AuditActions.ENTITY_APPOINTMENT, UUID.randomUUID(),
					Map.of("patientId", alice.getId().toString(), "doctorId", cardiologist.getId().toString(), "from",
							"WAITING", "to", "IN_CONSULTATION"));
			fixture.flush();
			mockMvc.perform(get("/api/v1/activity").cookie(cardiologistSession))
				.andExpect(jsonPath("$.content[?(@.action == 'APPOINTMENT_STATUS_CHANGED')].status")
					.value(contains("IN_CONSULTATION")));
		}

		@Test
		void secretariesHaveNoActivityFeed() throws Exception {
			mockMvc.perform(get("/api/v1/activity").cookie(secretarySession)).andExpect(status().isForbidden());
		}

		@Test
		void dashboardsIncludeRecentActivityInTheSameScope() throws Exception {
			mockMvc.perform(get("/api/v1/dashboard/owner").cookie(ownerSession))
				.andExpect(jsonPath("$.recentActivity", hasSize(3)));
			mockMvc.perform(get("/api/v1/dashboard/doctor").cookie(cardiologistSession))
				.andExpect(jsonPath("$.recentActivity[*].action").value(contains("APPOINTMENT_CREATED")));
		}

	}

	private Patient patient(Clinic owningClinic, String firstName, Doctor primary) {
		Patient patient = patients.save(new Patient(owningClinic.getId(), firstName, "Example",
				LocalDate.of(1980, 1, 1), primary.getUserId()));
		records.save(new MedicalRecord(owningClinic.getId(), patient.getId()));
		patientDoctors.save(new PatientDoctor(owningClinic.getId(), patient.getId(), primary.getId(),
				DoctorRelationshipType.PRIMARY, primary.getUserId()));
		fixture.flush();
		return patient;
	}

	/** Saved directly with its final status: fixtures may lie in the past, which the API refuses. */
	private Appointment appointment(Patient patient, Doctor doctor, LocalDate date, String time,
			AppointmentStatus status) {
		Instant start = date.atTime(LocalTime.parse(time)).atZone(PARIS).toInstant();
		Appointment appointment = appointments.save(new Appointment(patient.getClinicId(), patient.getId(),
				doctor.getId(), start, start.plus(30, ChronoUnit.MINUTES), AppointmentType.ROUTINE, null, null,
				doctor.getUserId()));
		fixture.flush();
		jdbc.update("update appointment set status = ? where id = ?", status.name(), appointment.getId());
		fixture.flushAndClear();
		return appointment;
	}

	private void consultation(Patient patient, Doctor doctor, LocalDate date) {
		jdbc.update("""
				insert into consultation (id, clinic_id, medical_record_id, doctor_id, consulted_at, created_by)
				select ?, ?, mr.id, ?, ?, ? from medical_record mr where mr.patient_id = ?
				""", UUID.randomUUID(), patient.getClinicId(), doctor.getId(),
				date.atTime(10, 0).atZone(PARIS).toOffsetDateTime(), doctor.getUserId(),
				patient.getId());
	}

	private void allergy(Patient patient, String allergen, String severity) {
		jdbc.update("""
				insert into allergy (id, medical_record_id, allergen, severity, created_by)
				select ?, mr.id, ?, ?, ? from medical_record mr where mr.patient_id = ?
				""", UUID.randomUUID(), allergen, severity, owner.getId(), patient.getId());
	}

	private void condition(Patient patient, String name, String status) {
		jdbc.update("""
				insert into medical_condition (id, medical_record_id, name, status, created_by)
				select ?, mr.id, ?, ?, ? from medical_record mr where mr.patient_id = ?
				""", UUID.randomUUID(), name, status, owner.getId(), patient.getId());
	}

}
