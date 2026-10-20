package com.iatros.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.notNullValue;
import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.security.RateLimiter;
import com.iatros.backend.shared.error.BusinessRuleException;
import com.iatros.backend.shared.i18n.Language;
import com.iatros.backend.shared.mail.EmailMessage;
import com.iatros.backend.shared.mail.EmailSender;
import com.iatros.backend.shared.mail.OutboxEmailSender;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.Role;
import com.iatros.backend.user.UserStatus;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowTests {

	static final String PASSWORD = "correct-horse-battery";
	static final String NEW_PASSWORD = "a-brand-new-passphrase";
	static final String SESSION_COOKIE = "IATROS_SESSION";
	private static final Pattern TOKEN_IN_LINK = Pattern.compile("#token=([A-Za-z0-9_-]+)");

	@Autowired private MockMvc mockMvc;
	@Autowired private ClinicRepository clinics;
	@Autowired private AppUserRepository users;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private InvitationService invitations;
	@Autowired private RateLimiter rateLimiter;
	@Autowired private EmailSender emailSender;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager entityManager;

	private Clinic clinic;
	private AppUser owner;
	private AppUser secretary;
	private String ownerEmail;

	@BeforeEach
	void setUp() {
		rateLimiter.reset();
		outbox().clear();
		clinic = clinics.save(new Clinic("Auth Test Clinic"));
		ownerEmail = unique("owner");
		owner = activeUser(ownerEmail, Role.OWNER);
		secretary = activeUser(unique("secretary"), Role.SECRETARY);
		flushAndClear();
	}

	@Nested
	class Login {

		@Test
		void returnsTheCurrentUserAndEstablishesASession() throws Exception {
			MvcResult result = login(ownerEmail, PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(owner.getId().toString()))
				.andExpect(jsonPath("$.role").value("OWNER"))
				.andExpect(jsonPath("$.clinic.id").value(clinic.getId().toString()))
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn();

			Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
			assertThat(session).isNotNull();
			assertThat(session.isHttpOnly()).isTrue();
			assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();

			mockMvc.perform(get("/api/v1/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(ownerEmail));
		}

		@Test
		void emailIsCaseInsensitive() throws Exception {
			login(ownerEmail.toUpperCase(), PASSWORD).andExpect(status().isOk());
		}

		@Test
		void wrongPasswordIsRejected() throws Exception {
			expectInvalidCredentials(login(ownerEmail, "wrong-password-123"));
		}

		@Test
		void unknownEmailIsRejectedTheSameWay() throws Exception {
			expectInvalidCredentials(login(unique("nobody"), PASSWORD));
		}

		@Test
		void invitedUserWithoutPasswordCannotLogIn() throws Exception {
			AppUser invited = users.save(new AppUser(clinic.getId(), "Ivy", "Invited", unique("invited"), Role.DOCTOR));
			flushAndClear();

			expectInvalidCredentials(login(invited.getEmail(), PASSWORD));
		}

		@Test
		void deactivatedUserCannotLogInEvenWithTheRightPassword() throws Exception {
			users.findById(secretary.getId()).orElseThrow().deactivate(java.time.Instant.now());
			flushAndClear();

			expectInvalidCredentials(login(secretary.getEmail(), PASSWORD));
		}

		@Test
		void usersOfASuspendedClinicCannotLogIn() throws Exception {
			jdbc.update("UPDATE clinic SET status = 'SUSPENDED' WHERE id = ?", clinic.getId());
			flushAndClear();

			expectInvalidCredentials(login(ownerEmail, PASSWORD));
		}

		@Test
		void requiresACsrfToken() throws Exception {
			mockMvc.perform(post("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(loginJson(ownerEmail, PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
		}

		@Test
		void successAndFailureAreAudited() throws Exception {
			login(ownerEmail, "wrong-password-123");
			login(ownerEmail, PASSWORD);

			assertThat(auditActions(owner.getId())).contains("LOGIN_FAILED", "LOGIN_SUCCEEDED");
		}

		@Test
		void repeatedAttemptsAreRateLimited() throws Exception {
			for (int i = 0; i < 10; i++) {
				login(ownerEmail, "wrong-password-123").andExpect(status().isUnauthorized());
			}

			login(ownerEmail, PASSWORD)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"))
				.andExpect(header().string("Retry-After", notNullValue()));
		}

	}

	@Nested
	class Sessions {

		@Test
		void meWithoutASessionIsUnauthenticated() throws Exception {
			mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		}

		@Test
		void logoutInvalidatesTheSession() throws Exception {
			Cookie session = sessionOf(ownerEmail, PASSWORD);

			mockMvc.perform(post("/api/v1/auth/logout").cookie(session).with(spaCsrf()))
				.andExpect(status().isNoContent());

			mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
			assertThat(auditActions(owner.getId())).contains("LOGOUT");
		}

		@Test
		void csrfEndpointIssuesTheCookie() throws Exception {
			MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent()).andReturn();
			Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
			assertThat(cookie).isNotNull();
			assertThat(cookie.isHttpOnly()).isFalse();
			assertThat(result.getRequest().getSession(false)).as("issuing a CSRF token creates no session").isNull();
		}

		@Test
		void sessionCookieIsHardenedAndIdleTimeoutApplies() throws Exception {
			MvcResult result = login(ownerEmail, PASSWORD).andExpect(status().isOk()).andReturn();

			assertThat(result.getResponse().getHeaders("Set-Cookie"))
				.filteredOn(header -> header.startsWith(SESSION_COOKIE + "="))
				.singleElement()
				.satisfies(header -> assertThat(header).contains("HttpOnly", "SameSite=Lax", "Path=/"));
			assertThat(jdbc.queryForList("SELECT max_inactive_interval FROM spring_session WHERE principal_name = ?",
					Integer.class, owner.getId().toString()))
				.containsOnly(30 * 60);
		}

		@Test
		void languagePreferenceIsSavedAndCanBeCleared() throws Exception {
			Cookie session = sessionOf(ownerEmail, PASSWORD);

			updatePreferences(session, "{\"preferredLanguage\": \"fr\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.preferredLanguage").value("fr"));
			mockMvc.perform(get("/api/v1/auth/me").cookie(session))
				.andExpect(jsonPath("$.preferredLanguage").value("fr"));

			updatePreferences(session, "{\"preferredLanguage\": null}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.preferredLanguage").doesNotExist());
		}

		@Test
		void unsupportedLanguageIsAValidationError() throws Exception {
			updatePreferences(sessionOf(ownerEmail, PASSWORD), "{\"preferredLanguage\": \"de\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("preferredLanguage"));
		}

		@Test
		void preferencesRequireASession() throws Exception {
			mockMvc.perform(put("/api/v1/auth/me/preferences").with(spaCsrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"preferredLanguage\": \"fr\"}"))
				.andExpect(status().isUnauthorized());
		}

		@Test
		void mismatchedCsrfTokenIsRejected() throws Exception {
			mockMvc.perform(post("/api/v1/auth/login")
					.cookie(new Cookie("XSRF-TOKEN", "cookie-value"))
					.header("X-XSRF-TOKEN", "different-value")
					.contentType(MediaType.APPLICATION_JSON)
					.content(loginJson(ownerEmail, PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
		}

	}

	@Nested
	class ChangePassword {

		@Test
		void wrongCurrentPasswordIsAFieldError() throws Exception {
			Cookie session = sessionOf(ownerEmail, PASSWORD);

			changePassword(session, "not-my-password", NEW_PASSWORD, NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("INCORRECT_PASSWORD"));
		}

		@Test
		void confirmationMustMatch() throws Exception {
			Cookie session = sessionOf(ownerEmail, PASSWORD);

			changePassword(session, PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "x")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("confirmPassword"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("PASSWORD_MISMATCH"));
		}

		@Test
		void shortPasswordsAreRejected() throws Exception {
			Cookie session = sessionOf(ownerEmail, PASSWORD);

			changePassword(session, PASSWORD, "short", "short")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID_LENGTH"));
		}

		@Test
		void replacesThePasswordAndSignsOutOtherSessions() throws Exception {
			Cookie current = sessionOf(ownerEmail, PASSWORD);
			Cookie other = sessionOf(ownerEmail, PASSWORD);

			changePassword(current, PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

			mockMvc.perform(get("/api/v1/auth/me").cookie(current)).andExpect(status().isOk());
			mockMvc.perform(get("/api/v1/auth/me").cookie(other)).andExpect(status().isUnauthorized());
			expectInvalidCredentials(login(ownerEmail, PASSWORD));
			login(ownerEmail, NEW_PASSWORD).andExpect(status().isOk());
		}

		@Test
		void requiresAuthentication() throws Exception {
			changePassword(null, PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isUnauthorized());
		}

	}

	@Nested
	class PasswordReset {

		@Test
		void unknownEmailGetsTheSameResponseAndNoEmail() throws Exception {
			forgotPassword(unique("nobody")).andExpect(status().isAccepted());

			assertThat(outbox().sentMessages()).isEmpty();
		}

		@Test
		void deactivatedUsersReceiveNoResetEmail() throws Exception {
			users.findById(secretary.getId()).orElseThrow().deactivate(java.time.Instant.now());
			flushAndClear();

			forgotPassword(secretary.getEmail()).andExpect(status().isAccepted());

			assertThat(outbox().sentMessages()).isEmpty();
		}

		@Test
		void resetLinkSetsANewPasswordOnceAndSignsOutEverywhere() throws Exception {
			Cookie existingSession = sessionOf(ownerEmail, PASSWORD);

			forgotPassword(ownerEmail).andExpect(status().isAccepted());
			EmailMessage email = singleEmailTo(ownerEmail);
			assertThat(email.body()).contains("http://localhost:5173/reset-password#token=");
			String token = tokenFrom(email);

			tokenPassword("/api/v1/auth/reset-password", token, NEW_PASSWORD).andExpect(status().isNoContent());

			mockMvc.perform(get("/api/v1/auth/me").cookie(existingSession)).andExpect(status().isUnauthorized());
			login(ownerEmail, NEW_PASSWORD).andExpect(status().isOk());
			tokenPassword("/api/v1/auth/reset-password", token, "yet-another-passphrase")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		}

		@Test
		void requestingAgainInvalidatesTheEarlierLink() throws Exception {
			forgotPassword(ownerEmail);
			String first = tokenFrom(singleEmailTo(ownerEmail));
			outbox().clear();
			forgotPassword(ownerEmail);

			tokenPassword("/api/v1/auth/reset-password", first, NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		}

		@Test
		void unknownTokensAreRejected() throws Exception {
			tokenPassword("/api/v1/auth/reset-password", "not-a-real-token", NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		}

		@Test
		void resetTokensCannotBeUsedAsInvitations() throws Exception {
			forgotPassword(ownerEmail);
			String token = tokenFrom(singleEmailTo(ownerEmail));

			tokenPassword("/api/v1/auth/accept-invitation", token, NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		}

	}

	@Nested
	class Invitations {

		@Test
		void acceptingActivatesTheAccountOnce() throws Exception {
			AppUser invited = users.save(new AppUser(clinic.getId(), "Ivy", "Invited", unique("invited"), Role.DOCTOR));
			invitations.sendInvitation(invited, owner.getId());
			String token = tokenFrom(singleEmailTo(invited.getEmail()));

			tokenPassword("/api/v1/auth/accept-invitation", token, NEW_PASSWORD).andExpect(status().isNoContent());

			flushAndClear();
			assertThat(users.findById(invited.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
			login(invited.getEmail(), NEW_PASSWORD).andExpect(status().isOk());
			tokenPassword("/api/v1/auth/accept-invitation", token, NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		}

		@Test
		void invitationIsWrittenInTheUsersLanguage() {
			AppUser invited = new AppUser(clinic.getId(), "Inès", "Invitée", unique("invitee"), Role.SECRETARY);
			invited.changePreferredLanguage(Language.FR);
			users.save(invited);

			invitations.sendInvitation(invited, owner.getId());

			EmailMessage email = singleEmailTo(invited.getEmail());
			assertThat(email.subject()).isEqualTo("Invitation à rejoindre Auth Test Clinic sur IATROS");
			assertThat(email.body()).contains("Bonjour Inès").contains("/accept-invitation#token=");
		}

		@Test
		void activeUsersCannotBeInvited() {
			AppUser active = users.findById(secretary.getId()).orElseThrow();

			assertThatThrownBy(() -> invitations.sendInvitation(active, owner.getId()))
				.isInstanceOf(BusinessRuleException.class);
		}

	}

	ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").with(spaCsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(loginJson(email, password)));
	}

	Cookie sessionOf(String email, String password) throws Exception {
		Cookie session = login(email, password).andExpect(status().isOk()).andReturn().getResponse()
			.getCookie(SESSION_COOKIE);
		assertThat(session).isNotNull();
		return session;
	}

	private ResultActions changePassword(Cookie session, String current, String next, String confirm)
			throws Exception {
		var request = post("/api/v1/auth/change-password").with(spaCsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword": "%s", "newPassword": "%s", "confirmPassword": "%s"}
					""".formatted(current, next, confirm));
		if (session != null) {
			request.cookie(session);
		}
		return mockMvc.perform(request);
	}

	private ResultActions updatePreferences(Cookie session, String json) throws Exception {
		return mockMvc.perform(put("/api/v1/auth/me/preferences").cookie(session).with(spaCsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions forgotPassword(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/forgot-password").with(spaCsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\"}".formatted(email)));
	}

	private ResultActions tokenPassword(String path, String token, String password) throws Exception {
		return mockMvc.perform(post(path).with(spaCsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"token": "%s", "newPassword": "%s", "confirmPassword": "%s"}
					""".formatted(token, password, password)));
	}

	private static void expectInvalidCredentials(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	private static String loginJson(String email, String password) {
		return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
	}

	private AppUser activeUser(String email, Role role) {
		AppUser user = new AppUser(clinic.getId(), "Test", role.name(), email, role);
		user.activate(passwordEncoder.encode(PASSWORD));
		return users.save(user);
	}

	private List<String> auditActions(UUID userId) {
		entityManager.flush();
		return jdbc.queryForList("SELECT action FROM audit_event WHERE actor_user_id = ?", String.class, userId);
	}

	private EmailMessage singleEmailTo(String recipient) {
		List<EmailMessage> sent = outbox().sentMessages();
		assertThat(sent).singleElement().extracting(EmailMessage::to).isEqualTo(recipient);
		return sent.getFirst();
	}

	private static String tokenFrom(EmailMessage email) {
		Matcher matcher = TOKEN_IN_LINK.matcher(email.body());
		assertThat(matcher.find()).as("email contains a token link").isTrue();
		return matcher.group(1);
	}

	private OutboxEmailSender outbox() {
		return (OutboxEmailSender) emailSender;
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	private static String unique(String prefix) {
		return prefix + "-" + UUID.randomUUID() + "@auth.test";
	}

}
