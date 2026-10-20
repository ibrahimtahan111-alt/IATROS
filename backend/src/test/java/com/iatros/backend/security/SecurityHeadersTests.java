package com.iatros.backend.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Browser-hardening headers on every API response (security.md §27, decisions.md D-045). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersTests {

	@Autowired private MockMvc mockMvc;

	@Test
	void publicResponsesAreHardened() throws Exception {
		expectHardened(mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent()));
	}

	@Test
	void rejectedResponsesAreHardenedToo() throws Exception {
		expectHardened(mockMvc.perform(get("/api/v1/patients")).andExpect(status().isUnauthorized()));
	}

	@Test
	void httpsResponsesRequireHttps() throws Exception {
		mockMvc.perform(get("/api/v1/auth/csrf").secure(true))
			.andExpect(header().string("Strict-Transport-Security", containsString("max-age=")));
	}

	private static void expectHardened(ResultActions response) throws Exception {
		response
			.andExpect(header().string("Content-Security-Policy", SecurityConfig.API_CONTENT_SECURITY_POLICY))
			.andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
			.andExpect(header().string("Referrer-Policy", "no-referrer"))
			.andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
			.andExpect(header().string("Permissions-Policy", SecurityConfig.PERMISSIONS_POLICY))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("Cache-Control", containsString("no-store")));
	}

}
