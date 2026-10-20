package com.iatros.backend.doctor;

import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.security.RateLimiter;
import com.iatros.backend.support.TestClinicFixture;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;

import jakarta.servlet.http.Cookie;

/** Doctor profiles (roadmap.md Phase 6, api.md §8): scoped reads and owner-or-self edits. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DoctorApiTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private TestClinicFixture fixture;
	@Autowired private RateLimiter rateLimiter;

	private Doctor cardiologist;
	private Doctor dermatologist;
	private Doctor otherClinicDoctor;
	private Cookie ownerSession;
	private Cookie secretarySession;
	private Cookie cardiologistSession;

	@BeforeEach
	void setUp() throws Exception {
		rateLimiter.reset();
		Clinic clinic = fixture.clinic("Doctor Clinic");
		AppUser owner = fixture.activeUser(clinic, Role.OWNER, "Olivia", "Owner");
		AppUser secretary = fixture.activeUser(clinic, Role.SECRETARY, "Sara", "Adams");
		AppUser daniel = fixture.activeUser(clinic, Role.DOCTOR, "Daniel", "Brown");
		AppUser chloe = fixture.activeUser(clinic, Role.DOCTOR, "Chloe", "Carter");
		cardiologist = fixture.doctorProfile(clinic, daniel, "CARDIOLOGY");
		dermatologist = fixture.doctorProfile(clinic, chloe, "DERMATOLOGY");
		Clinic otherClinic = fixture.clinic("Other Clinic");
		otherClinicDoctor = fixture.doctorProfile(otherClinic,
				fixture.activeUser(otherClinic, Role.DOCTOR, "Oscar", "Abbott"), "CARDIOLOGY");
		ownerSession = fixture.login(mockMvc, owner);
		secretarySession = fixture.login(mockMvc, secretary);
		cardiologistSession = fixture.login(mockMvc, daniel);
	}

	@Nested
	class Listing {

		@Test
		void ownersAndSecretariesSeeEveryDoctorOfTheirClinicOnly() throws Exception {
			for (Cookie session : new Cookie[] { ownerSession, secretarySession }) {
				mockMvc.perform(get("/api/v1/doctors").cookie(session))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[*].lastName").value(contains("Brown", "Carter")))
					.andExpect(jsonPath("$.content[0].primarySpecialty").value("CARDIOLOGY"))
					.andExpect(jsonPath("$.content[0].id").value(cardiologist.getId().toString()));
			}
		}

		@Test
		void aDoctorSeesOnlyThemselves() throws Exception {
			mockMvc.perform(get("/api/v1/doctors").cookie(cardiologistSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(cardiologist.getId().toString())));
		}

		@Test
		void filtersBySpecialtyAndSearch() throws Exception {
			mockMvc.perform(get("/api/v1/doctors").param("specialty", "DERMATOLOGY").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(dermatologist.getId().toString())));
			mockMvc.perform(get("/api/v1/doctors").param("search", "daniel").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(cardiologist.getId().toString())));
			mockMvc.perform(get("/api/v1/doctors").param("sort", "lastName,desc").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].lastName").value(contains("Carter", "Brown")));
		}

		@Test
		void rejectsAnUnknownSpecialty() throws Exception {
			mockMvc.perform(get("/api/v1/doctors").param("specialty", "ASTROLOGY").cookie(ownerSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'specialty')].code").value(contains("INVALID")));
		}

	}

	@Nested
	class Reading {

		@Test
		void returnsAClinicDoctorToOwnersAndSecretaries() throws Exception {
			mockMvc.perform(get("/api/v1/doctors/{id}", dermatologist.getId()).cookie(secretarySession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value("Chloe"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
		}

		@Test
		void hidesOtherClinicsDoctors() throws Exception {
			mockMvc.perform(get("/api/v1/doctors/{id}", otherClinicDoctor.getId()).cookie(ownerSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DOCTOR_NOT_FOUND"));
		}

		@Test
		void hidesOtherDoctorsFromADoctor() throws Exception {
			mockMvc.perform(get("/api/v1/doctors/{id}", dermatologist.getId()).cookie(cardiologistSession))
				.andExpect(status().isNotFound());
			mockMvc.perform(get("/api/v1/doctors/{id}", cardiologist.getId()).cookie(cardiologistSession))
				.andExpect(status().isOk());
		}

		@Test
		void requiresAuthentication() throws Exception {
			mockMvc.perform(get("/api/v1/doctors")).andExpect(status().isUnauthorized());
		}

	}

	@Nested
	class Editing {

		@Test
		void anOwnerUpdatesAnyClinicDoctor() throws Exception {
			update(ownerSession, dermatologist.getId(), "PEDIATRICS", "  Allergy  ", "RPPS-42", 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.primarySpecialty").value("PEDIATRICS"))
				.andExpect(jsonPath("$.additionalSpecialty").value("Allergy"))
				.andExpect(jsonPath("$.registrationNumber").value("RPPS-42"))
				.andExpect(jsonPath("$.version").value(1));
		}

		@Test
		void aDoctorUpdatesThemselvesButNotAColleague() throws Exception {
			update(cardiologistSession, cardiologist.getId(), "CARDIOLOGY", null, "RPPS-1", 0)
				.andExpect(status().isOk());
			update(cardiologistSession, dermatologist.getId(), "CARDIOLOGY", null, null, 0)
				.andExpect(status().isNotFound());
		}

		@Test
		void aSecretaryCannotEditDoctors() throws Exception {
			update(secretarySession, cardiologist.getId(), "CARDIOLOGY", null, null, 0)
				.andExpect(status().isForbidden());
		}

		@Test
		void anOwnerCannotEditAnotherClinicsDoctor() throws Exception {
			update(ownerSession, otherClinicDoctor.getId(), "CARDIOLOGY", null, null, 0)
				.andExpect(status().isNotFound());
		}

		@Test
		void rejectsAStaleVersionAndAnUnknownSpecialty() throws Exception {
			update(ownerSession, cardiologist.getId(), "CARDIOLOGY", null, null, 7)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
			update(ownerSession, cardiologist.getId(), "ASTROLOGY", null, null, 0)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'primarySpecialty')].code")
					.value(contains("INVALID")));
		}

		@Test
		void validatesRequiredFields() throws Exception {
			mockMvc.perform(put("/api/v1/doctors/{id}", cardiologist.getId()).with(spaCsrf()).cookie(ownerSession)
					.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("primarySpecialty", "version")));
		}

	}

	private ResultActions update(Cookie session, UUID doctorId, String specialty, String additional,
			String registration, long version) throws Exception {
		String body = """
				{"primarySpecialty": %s, "additionalSpecialty": %s, "registrationNumber": %s, "version": %d}"""
			.formatted(json(specialty), json(additional), json(registration), version);
		return mockMvc.perform(put("/api/v1/doctors/{id}", doctorId).with(spaCsrf()).cookie(session)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String json(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

}
