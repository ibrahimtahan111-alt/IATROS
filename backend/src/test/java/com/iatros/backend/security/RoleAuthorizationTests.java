package com.iatros.backend.security;

import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.Role;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;

/** Role checks with real sessions: method security reads the role from the session principal. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleAuthorizationTests {

	private static final String PASSWORD = "correct-horse-battery";

	@Autowired private MockMvc mockMvc;
	@Autowired private ClinicRepository clinics;
	@Autowired private AppUserRepository users;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private RateLimiter rateLimiter;
	@Autowired private EntityManager entityManager;

	private Clinic clinic;

	@BeforeEach
	void setUp() {
		rateLimiter.reset();
		clinic = clinics.save(new Clinic("Role Test Clinic"));
	}

	@Test
	void ownerOnlyOperationAllowsTheOwner() throws Exception {
		mockMvc.perform(get("/test/owner-only").cookie(sessionFor(Role.OWNER)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.clinicId").value(clinic.getId().toString()));
	}

	@Test
	void ownerOnlyOperationRejectsDoctorsAndSecretaries() throws Exception {
		for (Role role : new Role[] { Role.DOCTOR, Role.SECRETARY }) {
			mockMvc.perform(get("/test/owner-only").cookie(sessionFor(role)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
	}

	@Test
	void ownerOnlyOperationRejectsAnonymousCallers() throws Exception {
		mockMvc.perform(get("/test/owner-only"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	private Cookie sessionFor(Role role) throws Exception {
		String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@roles.test";
		AppUser user = new AppUser(clinic.getId(), "Test", role.name(), email, role);
		user.activate(passwordEncoder.encode(PASSWORD));
		users.save(user);
		entityManager.flush();
		return mockMvc.perform(post("/api/v1/auth/login").with(spaCsrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getCookie("IATROS_SESSION");
	}

	@TestConfiguration
	static class Config {

		@Bean
		OwnerOnlyController ownerOnlyController() {
			return new OwnerOnlyController();
		}

	}

	@RestController
	static class OwnerOnlyController {

		@GetMapping("/test/owner-only")
		@PreAuthorize("hasRole('OWNER')")
		Map<String, Object> ownerOnly(@AuthenticationPrincipal AuthenticatedUser user) {
			return Map.of("clinicId", user.clinicId());
		}

	}

}
