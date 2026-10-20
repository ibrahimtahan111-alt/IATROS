package com.iatros.backend.schedule;

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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
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

import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.security.RateLimiter;
import com.iatros.backend.support.TestClinicFixture;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;

import jakarta.servlet.http.Cookie;

/** Doctor schedules (roadmap.md Phase 6, api.md §20, decisions.md D-035). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScheduleApiTests {

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
	private static final String WEEK = """
			{"version": 0, "periods": [
			  {"dayOfWeek": 1, "startTime": "14:00", "endTime": "18:00"},
			  {"dayOfWeek": 1, "startTime": "09:00", "endTime": "12:00"},
			  {"dayOfWeek": 3, "startTime": "09:00", "endTime": "12:00"}
			]}""";

	@Autowired private MockMvc mockMvc;
	@Autowired private TestClinicFixture fixture;
	@Autowired private RateLimiter rateLimiter;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private DoctorRepository doctors;
	@Autowired private WorkingHoursService workingHours;

	private Doctor doctor;
	private Doctor colleague;
	private Doctor otherClinicDoctor;
	private AppUser doctorUser;
	private Cookie ownerSession;
	private Cookie secretarySession;
	private Cookie doctorSession;
	private LocalDate nextMonday;

	@BeforeEach
	void setUp() throws Exception {
		rateLimiter.reset();
		Clinic clinic = fixture.clinic("Schedule Clinic");
		AppUser owner = fixture.activeUser(clinic, Role.OWNER, "Olivia", "Owner");
		AppUser secretary = fixture.activeUser(clinic, Role.SECRETARY, "Sara", "Adams");
		doctorUser = fixture.activeUser(clinic, Role.DOCTOR, "Daniel", "Brown");
		doctor = fixture.doctorProfile(clinic, doctorUser, "CARDIOLOGY");
		colleague = fixture.doctorProfile(clinic, fixture.activeUser(clinic, Role.DOCTOR, "Chloe", "Carter"),
				"DERMATOLOGY");
		Clinic otherClinic = fixture.clinic("Other Clinic");
		otherClinicDoctor = fixture.doctorProfile(otherClinic,
				fixture.activeUser(otherClinic, Role.DOCTOR, "Oscar", "Abbott"), "CARDIOLOGY");
		ownerSession = fixture.login(mockMvc, owner);
		secretarySession = fixture.login(mockMvc, secretary);
		doctorSession = fixture.login(mockMvc, doctorUser);
		nextMonday = LocalDate.now(PARIS).plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
	}

	@Nested
	class WeeklySchedule {

		@Test
		void startsEmptyAndIsReadableByOwnersSecretariesAndTheDoctor() throws Exception {
			for (Cookie session : new Cookie[] { ownerSession, secretarySession, doctorSession }) {
				mockMvc.perform(get("/api/v1/doctors/{id}/schedule", doctor.getId()).cookie(session))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.timeZone").value("Europe/Paris"))
					.andExpect(jsonPath("$.version").value(0))
					.andExpect(jsonPath("$.periods").value(hasSize(0)));
			}
		}

		@Test
		void anOwnerReplacesTheWholeWeekAndPeriodsComeBackSorted() throws Exception {
			replace(ownerSession, doctor.getId(), WEEK)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.periods[*].startTime").value(contains("09:00", "14:00", "09:00")))
				.andExpect(jsonPath("$.periods[*].dayOfWeek").value(contains(1, 1, 3)));

			replace(ownerSession, doctor.getId(), """
					{"version": 1, "periods": [{"dayOfWeek": 5, "startTime": "08:30", "endTime": "12:30"}]}""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.periods[*].dayOfWeek").value(contains(5)));
			assertThat(auditCount("SCHEDULE_UPDATED", doctor.getId())).isEqualTo(2);
		}

		@Test
		void aDoctorManagesTheirOwnScheduleOnly() throws Exception {
			replace(doctorSession, doctor.getId(), WEEK).andExpect(status().isOk());
			replace(doctorSession, colleague.getId(), WEEK).andExpect(status().isNotFound());
			mockMvc.perform(get("/api/v1/doctors/{id}/schedule", colleague.getId()).cookie(doctorSession))
				.andExpect(status().isNotFound());
		}

		@Test
		void aSecretaryCannotChangeSchedules() throws Exception {
			replace(secretarySession, doctor.getId(), WEEK).andExpect(status().isForbidden());
		}

		@Test
		void otherClinicsDoctorsAreInvisible() throws Exception {
			replace(ownerSession, otherClinicDoctor.getId(), WEEK).andExpect(status().isNotFound());
			mockMvc.perform(get("/api/v1/doctors/{id}/schedule", otherClinicDoctor.getId()).cookie(ownerSession))
				.andExpect(status().isNotFound());
		}

		@Test
		void touchingPeriodsAreAllowed() throws Exception {
			replace(ownerSession, doctor.getId(), """
					{"version": 0, "periods": [
					  {"dayOfWeek": 2, "startTime": "09:00", "endTime": "12:00"},
					  {"dayOfWeek": 2, "startTime": "12:00", "endTime": "13:00"}
					]}""")
				.andExpect(status().isOk());
		}

		@Test
		void reportsInvertedAndOverlappingPeriodsByIndex() throws Exception {
			replace(ownerSession, doctor.getId(), """
					{"version": 0, "periods": [
					  {"dayOfWeek": 2, "startTime": "12:00", "endTime": "09:00"},
					  {"dayOfWeek": 3, "startTime": "10:00", "endTime": "10:00"}
					]}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field").value(contains("periods[0].endTime",
						"periods[1].endTime")))
				.andExpect(jsonPath("$.fieldErrors[*].code").value(contains("END_BEFORE_START", "END_BEFORE_START")));

			replace(ownerSession, doctor.getId(), """
					{"version": 0, "periods": [
					  {"dayOfWeek": 2, "startTime": "11:00", "endTime": "14:00"},
					  {"dayOfWeek": 2, "startTime": "09:00", "endTime": "12:00"}
					]}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'periods[0].startTime')].code")
					.value(contains("OVERLAP")));
		}

		@Test
		void validatesDaysAndTimeFormat() throws Exception {
			replace(ownerSession, doctor.getId(), """
					{"version": 0, "periods": [{"dayOfWeek": 8, "startTime": "09:00", "endTime": "12:00"}]}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field").value(contains("periods[0].dayOfWeek")));
			replace(ownerSession, doctor.getId(), """
					{"version": 0, "periods": [{"dayOfWeek": 1, "startTime": "9h", "endTime": "12:00"}]}""")
				.andExpect(status().isBadRequest());
		}

		@Test
		void rejectsAStaleVersionWithoutChangingTheWeek() throws Exception {
			replace(ownerSession, doctor.getId(), WEEK).andExpect(status().isOk());
			replace(doctorSession, doctor.getId(), WEEK)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
			mockMvc.perform(get("/api/v1/doctors/{id}/schedule", doctor.getId()).cookie(ownerSession))
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.periods").value(hasSize(3)));
		}

		@Test
		void scheduleChangesDoNotConflictWithProfileEdits() throws Exception {
			replace(ownerSession, doctor.getId(), WEEK).andExpect(status().isOk());
			mockMvc.perform(put("/api/v1/doctors/{id}", doctor.getId()).with(spaCsrf()).cookie(ownerSession)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"primarySpecialty\": \"CARDIOLOGY\", \"version\": 0}"))
				.andExpect(status().isOk());
		}

	}

	@Nested
	class Exceptions {

		@Test
		void createsListsAndDeletesExceptions() throws Exception {
			String allDay = createException(ownerSession, doctor.getId(), """
					{"date": "%s", "type": "UNAVAILABLE", "reason": "  Conference  "}""".formatted(nextMonday))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startTime").doesNotExist())
				.andExpect(jsonPath("$.reason").value("Conference"))
				.andReturn().getResponse().getContentAsString();
			createException(doctorSession, doctor.getId(), """
					{"date": "%s", "type": "EXTRA_AVAILABILITY", "startTime": "18:00", "endTime": "20:00"}"""
				.formatted(nextMonday.plusDays(2)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startTime").value("18:00"));

			mockMvc.perform(get("/api/v1/doctors/{id}/schedule/exceptions", doctor.getId()).cookie(secretarySession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].type").value(contains("UNAVAILABLE", "EXTRA_AVAILABILITY")));

			String id = com.jayway.jsonpath.JsonPath.read(allDay, "$.id");
			mockMvc.perform(delete("/api/v1/doctors/{id}/schedule/exceptions/{exceptionId}", doctor.getId(), id)
					.with(spaCsrf()).cookie(ownerSession))
				.andExpect(status().isNoContent());
			mockMvc.perform(delete("/api/v1/doctors/{id}/schedule/exceptions/{exceptionId}", doctor.getId(), id)
					.with(spaCsrf()).cookie(ownerSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SCHEDULE_EXCEPTION_NOT_FOUND"));
			assertThat(auditCount("SCHEDULE_EXCEPTION_DELETED", UUID.fromString(id))).isEqualTo(1);
		}

		@Test
		void anExceptionOfAnotherDoctorCannotBeDeletedThroughTheWrongPath() throws Exception {
			String body = createException(ownerSession, colleague.getId(), """
					{"date": "%s", "type": "UNAVAILABLE"}""".formatted(nextMonday))
				.andReturn().getResponse().getContentAsString();
			String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");
			mockMvc.perform(delete("/api/v1/doctors/{id}/schedule/exceptions/{exceptionId}", doctor.getId(), id)
					.with(spaCsrf()).cookie(doctorSession))
				.andExpect(status().isNotFound());
		}

		@Test
		void validatesDatesAndTimes() throws Exception {
			createException(ownerSession, doctor.getId(), """
					{"date": "%s", "type": "UNAVAILABLE"}""".formatted(LocalDate.now(PARIS).minusDays(2)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'date')].code").value(contains("DATE_IN_PAST")));
			createException(ownerSession, doctor.getId(), """
					{"date": "%s", "type": "EXTRA_AVAILABILITY"}""".formatted(nextMonday))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field").value(contains("startTime", "endTime")));
			createException(ownerSession, doctor.getId(), """
					{"date": "%s", "type": "UNAVAILABLE", "startTime": "10:00"}""".formatted(nextMonday))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'endTime')].code").value(contains("REQUIRED")));
			createException(ownerSession, doctor.getId(), """
					{"date": "%s", "type": "UNAVAILABLE", "startTime": "10:00", "endTime": "09:00"}"""
				.formatted(nextMonday))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'endTime')].code")
					.value(contains("END_BEFORE_START")));
		}

		@Test
		void aSecretaryCannotManageExceptions() throws Exception {
			createException(secretarySession, doctor.getId(), """
					{"date": "%s", "type": "UNAVAILABLE"}""".formatted(nextMonday))
				.andExpect(status().isForbidden());
		}

	}

	@Nested
	class WorkingHours {

		@Test
		void composesTheWeekWithExceptions() throws Exception {
			replace(ownerSession, doctor.getId(), WEEK).andExpect(status().isOk());
			createException(ownerSession, doctor.getId(), """
					{"date": "%s", "type": "UNAVAILABLE", "startTime": "10:00", "endTime": "11:00"}"""
				.formatted(nextMonday)).andExpect(status().isCreated());
			createException(ownerSession, doctor.getId(), """
					{"date": "%s", "type": "EXTRA_AVAILABILITY", "startTime": "12:00", "endTime": "13:00"}"""
				.formatted(nextMonday.plusDays(2))).andExpect(status().isCreated());

			mockMvc.perform(get("/api/v1/doctors/{id}/working-hours", doctor.getId()).cookie(secretarySession)
					.param("from", nextMonday.toString()).param("to", nextMonday.plusDays(2).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timeZone").value("Europe/Paris"))
				.andExpect(jsonPath("$.days[*].date").value(contains(nextMonday.toString(),
						nextMonday.plusDays(1).toString(), nextMonday.plusDays(2).toString())))
				.andExpect(jsonPath("$.days[0].ranges[*].start").value(contains("09:00", "11:00", "14:00")))
				.andExpect(jsonPath("$.days[1].ranges").value(hasSize(0)))
				.andExpect(jsonPath("$.days[2].ranges[*].end").value(contains("13:00")));
		}

		@Test
		void aDeactivatedDoctorHasNoWorkingHours() throws Exception {
			replace(ownerSession, doctor.getId(), WEEK).andExpect(status().isOk());
			jdbc.update("update app_user set status = 'DEACTIVATED', deactivated_at = now() where id = ?",
					doctorUser.getId());
			mockMvc.perform(get("/api/v1/doctors/{id}/working-hours", doctor.getId()).cookie(ownerSession)
					.param("from", nextMonday.toString()).param("to", nextMonday.toString()))
				.andExpect(jsonPath("$.days[0].ranges").value(hasSize(0)));
		}

		@Test
		void limitsTheRange() throws Exception {
			workingHoursRequest(nextMonday, nextMonday.minusDays(1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'to')].code").value(contains("END_BEFORE_START")));
			workingHoursRequest(nextMonday, nextMonday.plusDays(42))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'to')].code").value(contains("OUT_OF_RANGE")));
			workingHoursRequest(nextMonday, nextMonday.plusDays(41)).andExpect(status().isOk());
			mockMvc.perform(get("/api/v1/doctors/{id}/working-hours", doctor.getId()).cookie(ownerSession))
				.andExpect(status().isBadRequest());
		}

		@Test
		void availabilityChecksUseTheClinicTimeZone() throws Exception {
			replace(ownerSession, doctor.getId(), WEEK).andExpect(status().isOk());
			Doctor reloaded = doctors.findById(doctor.getId()).orElseThrow();
			Instant nineParis = ZonedDateTime.of(nextMonday, LocalTime.of(9, 0), PARIS).toInstant();
			assertThat(workingHours.isAvailable(reloaded, nineParis, nineParis.plusSeconds(1800))).isTrue();
			assertThat(workingHours.isAvailable(reloaded, nineParis.minusSeconds(1800), nineParis.plusSeconds(1800)))
				.isFalse();
			assertThat(workingHours.isAvailable(reloaded, nineParis.plusSeconds(3 * 3600 - 900),
					nineParis.plusSeconds(3 * 3600 + 900))).isFalse();
			assertThat(workingHours.isAvailable(reloaded, nineParis, nineParis)).isFalse();
		}

		private ResultActions workingHoursRequest(LocalDate from, LocalDate to) throws Exception {
			return mockMvc.perform(get("/api/v1/doctors/{id}/working-hours", doctor.getId()).cookie(ownerSession)
				.param("from", from.toString()).param("to", to.toString()));
		}

	}

	private ResultActions replace(Cookie session, UUID doctorId, String body) throws Exception {
		return mockMvc.perform(put("/api/v1/doctors/{id}/schedule", doctorId).with(spaCsrf()).cookie(session)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions createException(Cookie session, UUID doctorId, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/doctors/{id}/schedule/exceptions", doctorId).with(spaCsrf())
			.cookie(session).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private Integer auditCount(String action, UUID entityId) {
		fixture.flush();
		return jdbc.queryForObject("select count(*) from audit_event where action = ? and entity_id = ?",
				Integer.class, action, entityId);
	}

}
