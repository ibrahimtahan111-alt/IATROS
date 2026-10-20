package com.iatros.backend.clinic;

import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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

import com.iatros.backend.security.RateLimiter;
import com.iatros.backend.support.TestClinicFixture;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;

import jakarta.servlet.http.Cookie;

/** Clinic profile and settings (roadmap.md Phase 5): everyone reads their own clinic; only the owner edits it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClinicApiTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private TestClinicFixture fixture;
	@Autowired private RateLimiter rateLimiter;
	@Autowired private JdbcTemplate jdbc;

	private Clinic clinic;
	private Cookie ownerSession;
	private Cookie doctorSession;
	private Cookie secretarySession;

	@BeforeEach
	void setUp() throws Exception {
		rateLimiter.reset();
		clinic = fixture.clinic("Clinique du Parc");
		fixture.clinic("Another Clinic");
		ownerSession = fixture.login(mockMvc, fixture.activeUser(clinic, Role.OWNER, "Olivia", "Owner"));
		AppUser doctor = fixture.activeUser(clinic, Role.DOCTOR, "Daniel", "Doc");
		doctorSession = fixture.login(mockMvc, doctor);
		secretarySession = fixture.login(mockMvc, fixture.activeUser(clinic, Role.SECRETARY, "Sara", "Sec"));
	}

	@Test
	void everyRoleReadsItsOwnClinicAndSettings() throws Exception {
		for (Cookie session : List.of(ownerSession, doctorSession, secretarySession)) {
			mockMvc.perform(get("/api/v1/clinic").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(clinic.getId().toString()))
				.andExpect(jsonPath("$.name").value("Clinique du Parc"))
				.andExpect(jsonPath("$.settings.timeZone").value("Europe/Paris"))
				.andExpect(jsonPath("$.settings.defaultAppointmentMinutes").value(30))
				.andExpect(jsonPath("$.settings.defaultLanguage").value("en"))
				.andExpect(jsonPath("$.settings.weekStartDay").value(1));
		}
	}

	@Test
	void theOwnerUpdatesTheClinicProfile() throws Exception {
		updateProfile(ownerSession, """
				{"name": "  Clinique du Lac ", "email": "contact@lac.test", "phone": "", "address": "2 rue du Lac",
				 "version": 0}
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Clinique du Lac"))
			.andExpect(jsonPath("$.phone").value(nullValue()))
			.andExpect(jsonPath("$.address").value("2 rue du Lac"))
			.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(get("/api/v1/auth/me").cookie(doctorSession))
			.andExpect(jsonPath("$.clinic.name").value("Clinique du Lac"));
		assertThat(auditActions()).contains("CLINIC_UPDATED");
	}

	@Test
	void theOwnerUpdatesSettings() throws Exception {
		updateSettings(ownerSession, """
				{"timeZone": "America/Montreal", "defaultAppointmentMinutes": 20, "defaultLanguage": "fr",
				 "weekStartDay": 7, "version": 0}
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.settings.timeZone").value("America/Montreal"))
			.andExpect(jsonPath("$.settings.defaultAppointmentMinutes").value(20))
			.andExpect(jsonPath("$.settings.defaultLanguage").value("fr"))
			.andExpect(jsonPath("$.settings.weekStartDay").value(7))
			.andExpect(jsonPath("$.settings.version").value(1));

		mockMvc.perform(get("/api/v1/auth/me").cookie(secretarySession))
			.andExpect(jsonPath("$.clinic.defaultLanguage").value("fr"));
		assertThat(auditActions()).contains("CLINIC_SETTINGS_UPDATED");
	}

	@Test
	void onlyRegionTimeZonesAreAccepted() throws Exception {
		for (String zone : List.of("Mars/Olympus", "+02:00", "UTC+2")) {
			expectFieldError(updateSettings(ownerSession, settingsJson(zone, 30, "en", 1)), "timeZone", "INVALID");
		}
	}

	@Test
	void validatesSettingRanges() throws Exception {
		expectFieldError(updateSettings(ownerSession, settingsJson("Europe/Paris", 3, "en", 1)),
				"defaultAppointmentMinutes", "OUT_OF_RANGE");
		expectFieldError(updateSettings(ownerSession, settingsJson("Europe/Paris", 30, "de", 1)), "defaultLanguage",
				"INVALID_FORMAT");
		expectFieldError(updateSettings(ownerSession, settingsJson("Europe/Paris", 30, "en", 8)), "weekStartDay",
				"OUT_OF_RANGE");
	}

	@Test
	void staleEditsAreRejected() throws Exception {
		updateProfile(ownerSession, "{\"name\": \"Renamed\", \"version\": 3}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
	}

	@Test
	void doctorsAndSecretariesCannotEditTheClinic() throws Exception {
		for (Cookie session : List.of(doctorSession, secretarySession)) {
			updateProfile(session, "{\"name\": \"Taken over\", \"version\": 0}")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
			updateSettings(session, settingsJson("Europe/Paris", 30, "en", 1))
				.andExpect(status().isForbidden());
		}
		assertThat(jdbc.queryForObject("SELECT name FROM clinic WHERE id = ?", String.class, clinic.getId()))
			.isEqualTo("Clinique du Parc");
	}

	@Test
	void anonymousCallersAreRejected() throws Exception {
		mockMvc.perform(get("/api/v1/clinic")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/specialties")).andExpect(status().isUnauthorized());
	}

	@Test
	void everyRoleReadsTheSpecialtyCodes() throws Exception {
		for (Cookie session : List.of(ownerSession, doctorSession, secretarySession)) {
			mockMvc.perform(get("/api/v1/specialties").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].code").value(hasItem("GENERAL_PRACTICE")))
				.andExpect(jsonPath("$[0].id").isNotEmpty());
		}
	}

	private ResultActions updateProfile(Cookie session, String json) throws Exception {
		return mockMvc.perform(put("/api/v1/clinic").cookie(session).with(spaCsrf())
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions updateSettings(Cookie session, String json) throws Exception {
		return mockMvc.perform(put("/api/v1/clinic/settings").cookie(session).with(spaCsrf())
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String settingsJson(String zone, int minutes, String language, int weekStart) {
		return """
				{"timeZone": "%s", "defaultAppointmentMinutes": %d, "defaultLanguage": "%s", "weekStartDay": %d,
				 "version": 0}
				""".formatted(zone, minutes, language, weekStart);
	}

	private static void expectFieldError(ResultActions result, String field, String code) throws Exception {
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == '%s')].code".formatted(field)).value(contains(code)));
	}

	private List<String> auditActions() {
		fixture.flush();
		return jdbc.queryForList("SELECT action FROM audit_event WHERE clinic_id = ?", String.class, clinic.getId());
	}

}
