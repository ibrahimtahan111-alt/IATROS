package com.iatros.backend.appointment;

import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import com.iatros.backend.schedule.DoctorSchedulePeriod;
import com.iatros.backend.schedule.DoctorSchedulePeriodRepository;
import com.iatros.backend.schedule.ScheduleException;
import com.iatros.backend.schedule.ScheduleExceptionRepository;
import com.iatros.backend.security.RateLimiter;
import com.iatros.backend.support.TestClinicFixture;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** Appointments and calendar (roadmap.md Phase 9, api.md §17–19, decisions.md D-024–D-027, D-038). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppointmentApiTests {

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
	private static final String LIST = "/api/v1/appointments";
	private static final String ONE = "/api/v1/appointments/{id}";
	private static final String CALENDAR = "/api/v1/calendar/events";
	private static final String AVAILABILITY = "/api/v1/appointments/availability";

	@Autowired private MockMvc mockMvc;
	@Autowired private TestClinicFixture fixture;
	@Autowired private RateLimiter rateLimiter;
	@Autowired private PatientRepository patients;
	@Autowired private PatientDoctorRepository patientDoctors;
	@Autowired private MedicalRecordRepository records;
	@Autowired private AppointmentRepository appointments;
	@Autowired private DoctorSchedulePeriodRepository periods;
	@Autowired private ScheduleExceptionRepository exceptions;
	@Autowired private JdbcTemplate jdbc;

	private AppUser owner;
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
	private LocalDate day;

	@BeforeEach
	void setUp() throws Exception {
		rateLimiter.reset();
		Clinic clinic = fixture.clinic("Appointment Clinic");
		owner = fixture.activeUser(clinic, Role.OWNER, "Olivia", "Owner");
		AppUser secretary = fixture.activeUser(clinic, Role.SECRETARY, "Sara", "Adams");
		AppUser daniel = fixture.activeUser(clinic, Role.DOCTOR, "Daniel", "Brown");
		AppUser chloe = fixture.activeUser(clinic, Role.DOCTOR, "Chloe", "Carter");
		cardiologist = fixture.doctorProfile(clinic, daniel, "CARDIOLOGY");
		dermatologist = fixture.doctorProfile(clinic, chloe, "DERMATOLOGY");
		workEveryDay(cardiologist);
		workEveryDay(dermatologist);
		alice = patient(clinic, "Alice", cardiologist);
		bruno = patient(clinic, "Bruno", dermatologist);

		Clinic otherClinic = fixture.clinic("Other Clinic");
		AppUser oscar = fixture.activeUser(otherClinic, Role.DOCTOR, "Oscar", "Abbott");
		otherClinicDoctor = fixture.doctorProfile(otherClinic, oscar, "CARDIOLOGY");
		workEveryDay(otherClinicDoctor);
		otherClinicPatient = patient(otherClinic, "Zoe", otherClinicDoctor);

		day = LocalDate.now(PARIS).plusDays(2);
		ownerSession = fixture.login(mockMvc, owner);
		secretarySession = fixture.login(mockMvc, secretary);
		cardiologistSession = fixture.login(mockMvc, daniel);
		dermatologistSession = fixture.login(mockMvc, chloe);
	}

	@Nested
	class Booking {

		@Test
		void secretariesBookAnyDoctorWithAnyPatient() throws Exception {
			book(secretarySession, bruno, cardiologist, at(day, "10:00"), at(day, "10:30"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.patient.firstName").value("Bruno"))
				.andExpect(jsonPath("$.doctor.lastName").value("Brown"))
				.andExpect(jsonPath("$.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.reason").value("Check-up"))
				.andExpect(jsonPath("$.createdBy.lastName").value("Adams"))
				.andExpect(jsonPath("$.editable").value(true))
				.andExpect(jsonPath("$.cancellable").value(true))
				.andExpect(jsonPath("$.nextStatuses").value(contains("CONFIRMED")))
				.andExpect(jsonPath("$.version").value(0));
		}

		@Test
		void doctorsBookOnlyThemselvesWithTheirPatients() throws Exception {
			book(cardiologistSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30"))
				.andExpect(status().isCreated());
			book(cardiologistSession, alice, dermatologist, at(day, "11:00"), at(day, "11:30"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
			book(cardiologistSession, bruno, cardiologist, at(day, "11:00"), at(day, "11:30"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
		}

		@Test
		void otherClinicsDoctorsAndPatientsDoNotExist() throws Exception {
			book(ownerSession, alice, otherClinicDoctor, at(day, "10:00"), at(day, "10:30"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DOCTOR_NOT_FOUND"));
			book(ownerSession, otherClinicPatient, cardiologist, at(day, "10:00"), at(day, "10:30"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
		}

		@Test
		void bookingsMustFitTheDoctorsWorkingHours() throws Exception {
			book(ownerSession, alice, cardiologist, at(day, "17:45"), at(day, "18:15"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("DOCTOR_UNAVAILABLE"));

			exceptions.save(ScheduleException.extraAvailability(cardiologist.getId(), day, LocalTime.of(18, 0),
					LocalTime.of(20, 0), null, owner.getId()));
			fixture.flush();
			book(ownerSession, alice, cardiologist, at(day, "17:45"), at(day, "18:15"))
				.andExpect(status().isCreated());

			LocalDate holiday = day.plusDays(1);
			exceptions.save(ScheduleException.unavailableAllDay(cardiologist.getId(), holiday, null, owner.getId()));
			fixture.flush();
			book(ownerSession, alice, cardiologist, at(holiday, "10:00"), at(holiday, "10:30"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("DOCTOR_UNAVAILABLE"));
		}

		@Test
		void overlappingBookingsOfTheSameDoctorAreRejected() throws Exception {
			String first = id(book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			book(ownerSession, bruno, cardiologist, at(day, "10:15"), at(day, "10:45"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPOINTMENT_CONFLICT"));
			book(ownerSession, bruno, cardiologist, at(day, "10:30"), at(day, "11:00"))
				.andExpect(status().isCreated());
			book(ownerSession, bruno, dermatologist, at(day, "10:00"), at(day, "10:30"))
				.andExpect(status().isCreated());

			cancel(ownerSession, first, "Patient called", 0).andExpect(status().isOk());
			book(ownerSession, bruno, cardiologist, at(day, "10:00"), at(day, "10:30"))
				.andExpect(status().isCreated());
		}

		@Test
		void validatesTimes() throws Exception {
			LocalDate yesterday = LocalDate.now(PARIS).minusDays(1);
			book(ownerSession, alice, cardiologist, at(yesterday, "10:00"), at(yesterday, "10:30"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'startAt')].code").value(contains("DATE_IN_PAST")));
			book(ownerSession, alice, cardiologist, at(day, "10:30"), at(day, "10:00"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
			mockMvc.perform(post(LIST).with(spaCsrf()).cookie(ownerSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"patientId": "%s", "doctorId": "%s", "startAt": "%s", "endAt": "%s"}"""
					.formatted(alice.getId(), cardiologist.getId(), at(day, "10:00"), at(day, "10:30"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'type')].code").value(contains("REQUIRED")));
		}

		@Test
		void archivedPatientsCannotBeBooked() throws Exception {
			alice.archive(Instant.now(), owner.getId());
			fixture.flush();
			book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"));
		}

		@Test
		void bookingIsAuditedWithoutItsText() throws Exception {
			String id = id(book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			fixture.flush();
			String metadata = jdbc.queryForObject(
					"select metadata::text from audit_event where action = ? and entity_id = ?::uuid", String.class,
					AuditActions.APPOINTMENT_CREATED, id);
			assertThat(metadata).contains(alice.getId().toString()).doesNotContain("Check-up");
		}

	}

	@Nested
	class Reading {

		@Test
		void doctorsOnlySeeTheirOwnAppointments() throws Exception {
			String aliceVisit = id(book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			link(bruno, cardiologist);
			String brunoVisit = id(book(ownerSession, bruno, dermatologist, at(day, "10:00"), at(day, "10:30")));

			mockMvc.perform(get(ONE, aliceVisit).cookie(cardiologistSession)).andExpect(status().isOk());
			mockMvc.perform(get(ONE, brunoVisit).cookie(cardiologistSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
			mockMvc.perform(get(LIST).cookie(cardiologistSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(aliceVisit)));
			mockMvc.perform(get(LIST).param("doctorId", dermatologist.getId().toString()).cookie(cardiologistSession))
				.andExpect(jsonPath("$.content").value(empty()));
			mockMvc.perform(get(LIST).cookie(secretarySession))
				.andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(aliceVisit, brunoVisit)));
		}

		@Test
		void otherClinicsAppointmentsDoNotExist() throws Exception {
			Appointment foreign = appointments.save(new Appointment(otherClinicPatient.getClinicId(),
					otherClinicPatient.getId(), otherClinicDoctor.getId(), at(day, "10:00"), at(day, "10:30"),
					AppointmentType.ROUTINE, null, null, otherClinicDoctor.getUserId()));
			fixture.flush();
			mockMvc.perform(get(ONE, foreign.getId()).cookie(ownerSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
			cancel(ownerSession, foreign.getId().toString(), null, 0).andExpect(status().isNotFound());
			mockMvc.perform(get(LIST).cookie(ownerSession)).andExpect(jsonPath("$.content").value(empty()));
		}

		@Test
		void listFiltersByPatientStatusAndDates() throws Exception {
			String first = id(book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			String second = id(book(ownerSession, alice, cardiologist, at(day.plusDays(1), "10:00"),
					at(day.plusDays(1), "10:30")));
			book(ownerSession, bruno, dermatologist, at(day, "10:00"), at(day, "10:30"));
			cancel(ownerSession, first, null, 0);

			mockMvc.perform(get(LIST).param("patientId", alice.getId().toString()).cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(second, first)));
			mockMvc.perform(get(LIST).param("patientId", alice.getId().toString()).param("status", "CANCELLED")
				.cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(first)));
			mockMvc.perform(get(LIST).param("from", day.plusDays(1).toString()).param("to",
					day.plusDays(1).toString()).cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(second)));
			mockMvc.perform(get(LIST).param("from", day.toString()).param("to", day.minusDays(1).toString())
				.cookie(ownerSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("END_BEFORE_START"));
		}

		@Test
		void detailsShowTheLinkedConsultationOnlyToClinicians() throws Exception {
			Appointment visit = pastAppointment(alice, cardiologist);
			String consultation = JsonPath.read(mockMvc.perform(post("/api/v1/patients/{id}/consultations",
					alice.getId()).with(spaCsrf()).cookie(cardiologistSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"consultedAt": "%s", "reason": "Follow-up", "appointmentId": "%s"}"""
					.formatted(Instant.now().truncatedTo(ChronoUnit.SECONDS), visit.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.appointmentId").value(visit.getId().toString()))
				.andReturn().getResponse().getContentAsString(), "$.id");

			mockMvc.perform(get(ONE, visit.getId()).cookie(cardiologistSession))
				.andExpect(jsonPath("$.consultationId").value(consultation));
			mockMvc.perform(get(ONE, visit.getId()).cookie(secretarySession))
				.andExpect(jsonPath("$.consultationId").value(nullValue()));
		}

	}

	@Nested
	class ConsultationLink {

		@Test
		void anAppointmentHasAtMostOneConsultation() throws Exception {
			Appointment visit = pastAppointment(alice, cardiologist);
			linkConsultation(cardiologistSession, alice, visit.getId()).andExpect(status().isCreated());
			linkConsultation(cardiologistSession, alice, visit.getId())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONSULTATION_ALREADY_EXISTS"));
		}

		@Test
		void onlyTheDoctorsOwnAppointmentWithThePatientCanBeLinked() throws Exception {
			link(alice, dermatologist);
			Appointment dermatologyVisit = pastAppointment(alice, dermatologist);
			Appointment brunoVisit = pastAppointment(bruno, dermatologist, 2);
			linkConsultation(cardiologistSession, alice, dermatologyVisit.getId())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("appointmentId"));
			linkConsultation(dermatologistSession, alice, brunoVisit.getId())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID"));
		}

	}

	@Nested
	class Lifecycle {

		@Test
		void arrivalStatusesFollowD024AndTheirActors() throws Exception {
			String id = pastAppointment(alice, cardiologist).getId().toString();
			changeStatus(secretarySession, id, "WAITING", 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("WAITING"))
				.andExpect(jsonPath("$.nextStatuses").value(empty()))
				.andExpect(jsonPath("$.editable").value(false));
			changeStatus(secretarySession, id, "IN_CONSULTATION", 1)
				.andExpect(status().isForbidden());
			changeStatus(dermatologistSession, id, "IN_CONSULTATION", 1)
				.andExpect(status().isNotFound());
			changeStatus(cardiologistSession, id, "IN_CONSULTATION", 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nextStatuses").value(contains("COMPLETED")))
				.andExpect(jsonPath("$.cancellable").value(false));
			changeStatus(ownerSession, id, "COMPLETED", 2)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"));
			changeStatus(ownerSession, id, "WAITING", 3)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
			assertThat(auditCount(AuditActions.APPOINTMENT_STATUS_CHANGED, id)).isEqualTo(3);
		}

		@Test
		void arrivalStatusesWaitForTheAppointmentsDay() throws Exception {
			String id = id(book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			changeStatus(ownerSession, id, "NO_SHOW", 0)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_STARTED"));
			changeStatus(ownerSession, id, "CONFIRMED", 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
		}

		@Test
		void cancellationHasItsOwnOperationAndKeepsTheAppointment() throws Exception {
			String id = id(book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			changeStatus(ownerSession, id, "CANCELLED", 0)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
			cancel(secretarySession, id, "  Patient called  ", 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancellationReason").value("Patient called"))
				.andExpect(jsonPath("$.cancellable").value(false))
				.andExpect(jsonPath("$.editable").value(false));
			cancel(secretarySession, id, null, 1)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
			mockMvc.perform(get(ONE, id).cookie(ownerSession)).andExpect(jsonPath("$.status").value("CANCELLED"));
		}

		@Test
		void changesRequireTheLoadedVersion() throws Exception {
			String id = id(book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			changeStatus(ownerSession, id, "CONFIRMED", 0).andExpect(status().isOk());
			cancel(secretarySession, id, null, 0)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
			mockMvc.perform(put(ONE, id).with(spaCsrf()).cookie(ownerSession).contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\": \"ROUTINE\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
		}

		@Test
		void detailsAreEditableUntilTheVisitStarts() throws Exception {
			String id = id(book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			updateDetails(cardiologistSession, id, "Private remark", 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("FOLLOW_UP"))
				.andExpect(jsonPath("$.notes").value("Private remark"));
			fixture.flush();
			String metadata = jdbc.queryForObject(
					"select metadata::text from audit_event where action = ? and entity_id = ?::uuid", String.class,
					AuditActions.APPOINTMENT_UPDATED, id);
			assertThat(metadata).contains("notes").doesNotContain("Private remark");

			cancel(ownerSession, id, null, 1);
			updateDetails(ownerSession, id, "Too late", 2)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_EDITABLE"));
		}

		@Test
		void reschedulingChecksTheNewTimeButNotItself() throws Exception {
			String id = id(book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			reschedule(ownerSession, id, cardiologist, at(day, "10:15"), at(day, "10:45"), 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.startAt").value(at(day, "10:15").toString()));
			book(ownerSession, bruno, dermatologist, at(day, "14:00"), at(day, "14:30"));
			reschedule(ownerSession, id, dermatologist, at(day, "14:00"), at(day, "14:30"), 1)
				.andExpect(status().isConflict());
			reschedule(secretarySession, id, dermatologist, at(day, "15:00"), at(day, "15:30"), 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.doctor.id").value(dermatologist.getId().toString()));
			reschedule(ownerSession, id, dermatologist, at(day, "20:00"), at(day, "20:30"), 2)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("DOCTOR_UNAVAILABLE"));
		}

		@Test
		void doctorsCannotMoveAppointmentsToAnotherDoctor() throws Exception {
			String id = id(book(cardiologistSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			reschedule(cardiologistSession, id, dermatologist, at(day, "11:00"), at(day, "11:30"), 0)
				.andExpect(status().isForbidden());
			reschedule(cardiologistSession, id, cardiologist, at(day, "11:00"), at(day, "11:30"), 0)
				.andExpect(status().isOk());
		}

	}

	@Nested
	class Calendar {

		@Test
		void ownersAndSecretariesSeeEveryDoctor() throws Exception {
			String visit = id(book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			String cancelled = id(book(ownerSession, bruno, dermatologist, at(day, "10:00"), at(day, "10:30")));
			cancel(ownerSession, cancelled, null, 0);

			for (Cookie session : new Cookie[] { ownerSession, secretarySession }) {
				mockMvc.perform(get(CALENDAR).param("from", day.toString()).param("to", day.plusDays(6).toString())
					.cookie(session))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.timeZone").value("Europe/Paris"))
					.andExpect(jsonPath("$.events[*].id").value(containsInAnyOrder(visit, cancelled)))
					.andExpect(jsonPath("$.events[?(@.id == '%s')].patient.firstName".formatted(visit))
						.value(contains("Alice")))
					.andExpect(jsonPath("$.doctors[*].lastName").value(contains("Brown", "Carter")))
					.andExpect(jsonPath("$.doctors[0].days", hasSize(7)))
					.andExpect(jsonPath("$.doctors[0].days[0].ranges[0].start").value("08:00"));
			}
			mockMvc.perform(get(CALENDAR).param("from", day.toString()).param("to", day.toString())
				.param("status", "SCHEDULED").cookie(ownerSession))
				.andExpect(jsonPath("$.events[*].id").value(contains(visit)));
		}

		@Test
		void doctorsSeeOnlyTheirOwnCalendar() throws Exception {
			String own = id(book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30")));
			book(ownerSession, bruno, dermatologist, at(day, "10:00"), at(day, "10:30"));
			mockMvc.perform(get(CALENDAR).param("from", day.toString()).param("to", day.toString())
				.cookie(cardiologistSession))
				.andExpect(jsonPath("$.events[*].id").value(contains(own)))
				.andExpect(jsonPath("$.doctors[*].id").value(contains(cardiologist.getId().toString())));
			mockMvc.perform(get(CALENDAR).param("from", day.toString()).param("to", day.toString())
				.param("doctorId", dermatologist.getId().toString()).cookie(cardiologistSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DOCTOR_NOT_FOUND"));
			mockMvc.perform(get(CALENDAR).param("from", day.toString()).param("to", day.toString())
				.param("doctorId", otherClinicDoctor.getId().toString()).cookie(ownerSession))
				.andExpect(status().isNotFound());
		}

		@Test
		void rangesAreLimited() throws Exception {
			mockMvc.perform(get(CALENDAR).param("from", day.toString()).param("to", day.plusDays(42).toString())
				.cookie(ownerSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("OUT_OF_RANGE"));
			mockMvc.perform(get(CALENDAR).param("from", day.toString()).param("to", day.minusDays(1).toString())
				.cookie(ownerSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].code").value("END_BEFORE_START"));
		}

		@Test
		void availabilityOffersFreeStartTimes() throws Exception {
			book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30"));
			mockMvc.perform(get(AVAILABILITY).param("doctorId", cardiologist.getId().toString())
				.param("date", day.toString()).param("duration", "30").cookie(secretarySession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slots[0].startAt").value(at(day, "08:00").toString()))
				.andExpect(jsonPath("$.slots[*].startAt").value(hasItem(at(day, "09:30").toString())))
				.andExpect(jsonPath("$.slots[*].startAt").value(not(hasItem(at(day, "09:45").toString()))))
				.andExpect(jsonPath("$.slots[*].startAt").value(not(hasItem(at(day, "10:15").toString()))))
				.andExpect(jsonPath("$.slots[*].startAt").value(hasItem(at(day, "10:30").toString())))
				.andExpect(jsonPath("$.slots[-1].startAt").value(at(day, "17:30").toString()));
			mockMvc.perform(get(AVAILABILITY).param("doctorId", dermatologist.getId().toString())
				.param("date", day.toString()).cookie(cardiologistSession))
				.andExpect(status().isNotFound());
			mockMvc.perform(get(AVAILABILITY).param("doctorId", cardiologist.getId().toString())
				.param("date", day.toString()).param("duration", "1").cookie(ownerSession))
				.andExpect(status().isBadRequest());
		}

	}

	@Nested
	class PatientTimes {

		@Test
		void patientsShowTheirNextAppointmentAndLastVisitWithinScope() throws Exception {
			link(alice, dermatologist);
			Appointment visit = pastAppointment(alice, cardiologist);
			changeStatus(ownerSession, visit.getId().toString(), "WAITING", 0);
			changeStatus(ownerSession, visit.getId().toString(), "IN_CONSULTATION", 1);
			changeStatus(ownerSession, visit.getId().toString(), "COMPLETED", 2);
			book(ownerSession, alice, cardiologist, at(day, "10:00"), at(day, "10:30"));

			mockMvc.perform(get("/api/v1/patients/{id}", alice.getId()).cookie(secretarySession))
				.andExpect(jsonPath("$.nextAppointmentAt").value(at(day, "10:00").toString()))
				.andExpect(jsonPath("$.lastVisitAt").value(visit.getStartAt().toString()));
			mockMvc.perform(get("/api/v1/patients").param("search", "Alice").cookie(ownerSession))
				.andExpect(jsonPath("$.content[0].nextAppointmentAt").value(at(day, "10:00").toString()));
			mockMvc.perform(get("/api/v1/patients/{id}", alice.getId()).cookie(dermatologistSession))
				.andExpect(jsonPath("$.nextAppointmentAt").value(nullValue()))
				.andExpect(jsonPath("$.lastVisitAt").value(nullValue()));
		}

	}

	private void workEveryDay(Doctor doctor) {
		for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
			periods.save(new DoctorSchedulePeriod(doctor.getId(), dayOfWeek, LocalTime.of(8, 0), LocalTime.of(18, 0)));
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

	private void link(Patient patient, Doctor doctor) {
		patientDoctors.save(new PatientDoctor(patient.getClinicId(), patient.getId(), doctor.getId(),
				DoctorRelationshipType.AUTHORIZED, owner.getId()));
		fixture.flush();
	}

	/** An appointment that started an hour ago; booked directly, since the API refuses past times. */
	private Appointment pastAppointment(Patient patient, Doctor doctor) {
		return pastAppointment(patient, doctor, 1);
	}

	private Appointment pastAppointment(Patient patient, Doctor doctor, int hoursAgo) {
		Instant start = Instant.now().minus(hoursAgo, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
		Appointment appointment = appointments.save(new Appointment(patient.getClinicId(), patient.getId(),
				doctor.getId(), start, start.plus(30, ChronoUnit.MINUTES), AppointmentType.ROUTINE, null, null,
				owner.getId()));
		fixture.flush();
		return appointment;
	}

	private int auditCount(String action, String entityId) {
		fixture.flush();
		return jdbc.queryForObject("select count(*) from audit_event where action = ? and entity_id = ?::uuid",
				Integer.class, action, entityId);
	}

	private static Instant at(LocalDate date, String time) {
		return date.atTime(LocalTime.parse(time)).atZone(PARIS).toInstant();
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions book(Cookie session, Patient patient, Doctor doctor, Instant start, Instant end)
			throws Exception {
		return mockMvc.perform(post(LIST).with(spaCsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"patientId": "%s", "doctorId": "%s", "startAt": "%s", "endAt": "%s", "type": "ROUTINE",
					 "reason": "Check-up"}"""
				.formatted(patient.getId(), doctor.getId(), start, end)));
	}

	private ResultActions updateDetails(Cookie session, String id, String notes, long version) throws Exception {
		return mockMvc.perform(put(ONE, id).with(spaCsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"type": "FOLLOW_UP", "reason": "Check-up", "notes": "%s", "version": %d}"""
				.formatted(notes, version)));
	}

	private ResultActions reschedule(Cookie session, String id, Doctor doctor, Instant start, Instant end,
			long version) throws Exception {
		return mockMvc.perform(patch(ONE + "/schedule", id).with(spaCsrf()).cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"doctorId": "%s", "startAt": "%s", "endAt": "%s", "version": %d}"""
				.formatted(doctor.getId(), start, end, version)));
	}

	private ResultActions changeStatus(Cookie session, String id, String status, long version) throws Exception {
		return mockMvc.perform(patch(ONE + "/status", id).with(spaCsrf()).cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\": \"%s\", \"version\": %d}".formatted(status, version)));
	}

	private ResultActions cancel(Cookie session, String id, String reason, long version) throws Exception {
		String value = reason == null ? "null" : "\"" + reason + "\"";
		return mockMvc.perform(post(ONE + "/cancel", id).with(spaCsrf()).cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\": %s, \"version\": %d}".formatted(value, version)));
	}

	private ResultActions linkConsultation(Cookie session, Patient patient, UUID appointmentId) throws Exception {
		return mockMvc.perform(post("/api/v1/patients/{id}/consultations", patient.getId()).with(spaCsrf())
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"consultedAt": "%s", "reason": "Follow-up", "appointmentId": "%s"}"""
				.formatted(Instant.now().truncatedTo(ChronoUnit.SECONDS), appointmentId)));
	}

}
