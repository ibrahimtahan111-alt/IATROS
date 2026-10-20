package com.iatros.backend.shared.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.shared.web.PageRequests;
import com.iatros.backend.shared.web.RequestIdFilter;
import com.iatros.backend.shared.web.SortWhitelist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTests {

	private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

	@Autowired
	private MockMvc mockMvc;

	@Nested
	@WithMockUser
	class ApplicationErrors {

		@Test
		void notFoundUsesTheExceptionCodeAndHidesTheMessage() throws Exception {
			mockMvc.perform(get("/test/errors/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"))
				.andExpect(jsonPath("$.timestamp", notNullValue()))
				.andExpect(jsonPath("$.instance").value("/test/errors/not-found"))
				.andExpect(jsonPath("$.detail").doesNotExist())
				.andExpect(content().string(not(containsString("internal message"))));
		}

		@Test
		void businessRuleViolationIs422() throws Exception {
			mockMvc.perform(get("/test/errors/business-rule"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
		}

		@Test
		void conflictIs409() throws Exception {
			mockMvc.perform(get("/test/errors/conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPOINTMENT_CONFLICT"));
		}

		@Test
		void forbiddenOperationIs403() throws Exception {
			mockMvc.perform(get("/test/errors/forbidden-operation"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
		}

		@Test
		void optimisticLockFailureIsConcurrentModification() throws Exception {
			mockMvc.perform(get("/test/errors/optimistic-lock"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
		}

		@Test
		void accessDeniedForAuthenticatedUserIs403() throws Exception {
			mockMvc.perform(get("/test/errors/access-denied"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}

		@Test
		void unexpectedErrorsRevealNothing() throws Exception {
			mockMvc.perform(get("/test/errors/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(content().string(not(containsString("secret-value"))))
				.andExpect(content().string(not(containsString("IllegalStateException"))));
		}

		@Test
		void unsupportedSortIsRejected() throws Exception {
			mockMvc.perform(get("/test/errors/page").param("sort", "passwordHash,asc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SORT"));
		}

	}

	@Nested
	@WithMockUser
	class RequestErrors {

		@Test
		void invalidBodyListsFieldCodes() throws Exception {
			mockMvc.perform(post("/test/errors/validated").with(spaCsrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name": " ", "email": "not-an-email", "note": "%s"}
							""".formatted("x".repeat(11))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].code").value("REQUIRED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')].code").value("INVALID_EMAIL"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'note')].code").value("INVALID_LENGTH"))
				.andExpect(content().string(not(containsString("not-an-email"))));
		}

		@Test
		void invalidRequestParameterListsFieldCodes() throws Exception {
			mockMvc.perform(get("/test/errors/param").param("limit", "500"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("limit"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("OUT_OF_RANGE"));
		}

		@Test
		void malformedJsonIsRejectedWithoutEchoingInput() throws Exception {
			mockMvc.perform(post("/test/errors/validated").with(spaCsrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\": \"secret-value\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
				.andExpect(content().string(not(containsString("secret-value"))));
		}

		@Test
		void wrongMethodIs405() throws Exception {
			mockMvc.perform(post("/test/errors/not-found").with(spaCsrf()))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		}

		@Test
		void unsupportedMediaTypeIs415() throws Exception {
			mockMvc.perform(post("/test/errors/validated").with(spaCsrf())
					.contentType(MediaType.TEXT_PLAIN)
					.content("hello"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
		}

		@Test
		void everyResponseCarriesARequestId() throws Exception {
			mockMvc.perform(get("/test/errors/not-found"))
				.andExpect(header().string(RequestIdFilter.HEADER, notNullValue()));
		}

		@Test
		void wellFormedIncomingRequestIdIsReused() throws Exception {
			mockMvc.perform(get("/test/errors/not-found").header(RequestIdFilter.HEADER, "proxy-1234-abcd"))
				.andExpect(header().string(RequestIdFilter.HEADER, "proxy-1234-abcd"));
		}

		@Test
		void unsafeIncomingRequestIdIsReplaced() throws Exception {
			mockMvc.perform(get("/test/errors/not-found").header(RequestIdFilter.HEADER, "bad\nid injected"))
				.andExpect(header().string(RequestIdFilter.HEADER, not(containsString("injected"))));
		}

	}

	@Nested
	class SecurityErrors {

		@Test
		void unauthenticatedRequestGetsProblemResponse() throws Exception {
			mockMvc.perform(get("/test/errors/not-found"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		}

		@Test
		@WithMockUser
		void missingCsrfTokenGetsProblemResponse() throws Exception {
			mockMvc.perform(post("/test/errors/validated").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
		}

	}

	record TestBody(@NotBlank String name, @Email String email, @Size(max = 10) String note) {
	}

	@TestConfiguration
	static class Config {

		@Bean
		ErrorTestController errorTestController() {
			return new ErrorTestController();
		}

	}

	@RestController
	@RequestMapping("/test/errors")
	static class ErrorTestController {

		private static final SortWhitelist SORT = SortWhitelist.of(Sort.by("lastName"), Map.of("lastName", "lastName"));

		@GetMapping("/not-found")
		void notFound() {
			throw new NotFoundException("PATIENT_NOT_FOUND", "internal message");
		}

		@GetMapping("/business-rule")
		void businessRule() {
			throw new BusinessRuleException("INVALID_STATUS_TRANSITION", "internal message");
		}

		@GetMapping("/conflict")
		void conflict() {
			throw new ConflictException("APPOINTMENT_CONFLICT", "internal message");
		}

		@GetMapping("/forbidden-operation")
		void forbiddenOperation() {
			throw new ForbiddenOperationException("ROLE_NOT_ALLOWED", "internal message");
		}

		@GetMapping("/optimistic-lock")
		void optimisticLock() {
			throw new ObjectOptimisticLockingFailureException(Object.class, "id");
		}

		@GetMapping("/access-denied")
		void accessDenied() {
			throw new AccessDeniedException("internal message");
		}

		@GetMapping("/unexpected")
		void unexpected() {
			throw new IllegalStateException("secret-value");
		}

		@GetMapping("/page")
		void page(@RequestParam(required = false) List<String> sort) {
			PageRequests.of(null, null, sort, SORT);
		}

		@GetMapping("/param")
		void param(@RequestParam @Max(100) int limit) {
		}

		@PostMapping("/validated")
		void validated(@Valid @RequestBody TestBody body) {
		}

	}

}
