package com.iatros.backend.staff;

import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static com.iatros.backend.support.TestClinicFixture.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.appointment.Appointment;
import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.appointment.AppointmentType;
import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.patient.DoctorRelationshipType;
import com.iatros.backend.patient.Patient;
import com.iatros.backend.patient.PatientDoctor;
import com.iatros.backend.patient.PatientDoctorRepository;
import com.iatros.backend.patient.PatientRepository;
import com.iatros.backend.security.RateLimiter;
import com.iatros.backend.shared.mail.EmailMessage;
import com.iatros.backend.shared.mail.EmailSender;
import com.iatros.backend.shared.mail.OutboxEmailSender;
import com.iatros.backend.support.TestClinicFixture;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;

import jakarta.servlet.http.Cookie;

/** Staff management (roadmap.md Phase 5): an owner can build a working team; nobody else can manage staff. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaffApiTests {

	private static final Pattern TOKEN_IN_LINK = Pattern.compile("#token=([A-Za-z0-9_-]+)");

	@Autowired private MockMvc mockMvc;
	@Autowired private TestClinicFixture fixture;
	@Autowired private EmailSender emailSender;
	@Autowired private RateLimiter rateLimiter;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private PatientRepository patients;
	@Autowired private PatientDoctorRepository patientDoctors;
	@Autowired private AppointmentRepository appointments;

	private Clinic clinic;
	private AppUser owner;
	private AppUser doctor;
	private Doctor doctorProfile;
	private AppUser secretary;
	private AppUser otherClinicMember;
	private Cookie ownerSession;

	@BeforeEach
	void setUp() throws Exception {
		rateLimiter.reset();
		clinic = fixture.clinic("Staff Clinic");
		owner = fixture.activeUser(clinic, Role.OWNER, "Olivia", "Owner");
		doctor = fixture.activeUser(clinic, Role.DOCTOR, "Daniel", "Brown");
		doctorProfile = fixture.doctorProfile(clinic, doctor, "CARDIOLOGY");
		secretary = fixture.activeUser(clinic, Role.SECRETARY, "Sara", "Adams");
		Clinic otherClinic = fixture.clinic("Other Clinic");
		otherClinicMember = fixture.activeUser(otherClinic, Role.SECRETARY, "Oscar", "Aaronson");
		ownerSession = fixture.login(mockMvc, owner);
		outbox().clear();
	}

	@Nested
	class Listing {

		@Test
		void listsOnlyTheClinicsMembersSortedByLastNameWithDoctorProfiles() throws Exception {
			mockMvc.perform(get("/api/v1/staff").cookie(ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].lastName").value(contains("Adams", "Brown", "Owner")))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[1].doctor.primarySpecialty").value("CARDIOLOGY"))
				.andExpect(jsonPath("$.content[0].doctor").value(nullValue()))
				.andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
		}

		@Test
		void searchesNamesAndEmailsCaseInsensitively() throws Exception {
			mockMvc.perform(get("/api/v1/staff").param("search", "daniel b").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(doctor.getId().toString())));
			mockMvc.perform(get("/api/v1/staff").param("search", secretary.getEmail().toUpperCase())
					.cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(secretary.getId().toString())));
		}

		@Test
		void treatsLikeWildcardsInTheSearchAsPlainText() throws Exception {
			mockMvc.perform(get("/api/v1/staff").param("search", "%").cookie(ownerSession))
				.andExpect(jsonPath("$.content").value(hasSize(0)));
		}

		@Test
		void filtersByRoleAndStatusAndPaginates() throws Exception {
			mockMvc.perform(get("/api/v1/staff").param("role", "DOCTOR").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].id").value(contains(doctor.getId().toString())));
			mockMvc.perform(get("/api/v1/staff").param("status", "INVITED").cookie(ownerSession))
				.andExpect(jsonPath("$.totalElements").value(0));
			mockMvc.perform(get("/api/v1/staff").param("size", "2").param("page", "1").cookie(ownerSession))
				.andExpect(jsonPath("$.content[*].lastName").value(contains("Owner")))
				.andExpect(jsonPath("$.totalPages").value(2));
		}

		@Test
		void sortsByAWhitelistedFieldInEitherDirection() throws Exception {
			mockMvc.perform(get("/api/v1/staff").param("sort", "lastName,desc").cookie(ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].lastName").value(contains("Owner", "Brown", "Adams")));
			mockMvc.perform(get("/api/v1/staff").param("sort", "role,asc").param("sort", "lastName,desc")
					.cookie(ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3));
		}

		@Test
		void rejectsUnknownSortFields() throws Exception {
			mockMvc.perform(get("/api/v1/staff").param("sort", "passwordHash,asc").cookie(ownerSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SORT"));
		}

	}

	@Nested
	class Creation {

		@Test
		void createsAnInvitedSecretaryAndEmailsTheInvitation() throws Exception {
			String email = TestClinicFixture.uniqueEmail("new-secretary");
			mockMvc.perform(withBody(post("/api/v1/staff"), """
					{"firstName": " Nina ", "lastName": "Novak", "email": "%s", "phone": " ", "role": "SECRETARY"}
					""".formatted(email)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.firstName").value("Nina"))
				.andExpect(jsonPath("$.phone").value(nullValue()))
				.andExpect(jsonPath("$.role").value("SECRETARY"))
				.andExpect(jsonPath("$.status").value("INVITED"))
				.andExpect(jsonPath("$.doctor").value(nullValue()));

			assertThat(outbox().sentMessages()).singleElement().extracting(EmailMessage::to).isEqualTo(email);
			assertThat(auditActions()).contains("STAFF_CREATED", "INVITATION_SENT");
		}

		@Test
		void createsADoctorWithTheirProfileInOneStep() throws Exception {
			mockMvc.perform(withBody(post("/api/v1/staff"), """
					{"firstName": "Hana", "lastName": "Ito", "email": "%s", "role": "DOCTOR",
					 "doctor": {"primarySpecialty": "PEDIATRICS", "additionalSpecialty": "Neonatology",
					            "registrationNumber": "RPPS-123"}}
					""".formatted(TestClinicFixture.uniqueEmail("new-doctor"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.doctor.id").isNotEmpty())
				.andExpect(jsonPath("$.doctor.primarySpecialty").value("PEDIATRICS"))
				.andExpect(jsonPath("$.doctor.additionalSpecialty").value("Neonatology"))
				.andExpect(jsonPath("$.doctor.registrationNumber").value("RPPS-123"));
		}

		@Test
		void theInvitedMemberActivatesTheirAccountAndSignsIn() throws Exception {
			String email = TestClinicFixture.uniqueEmail("team");
			mockMvc.perform(withBody(post("/api/v1/staff"), """
					{"firstName": "Tom", "lastName": "Team", "email": "%s", "role": "SECRETARY"}
					""".formatted(email)))
				.andExpect(status().isCreated());

			acceptInvitation(tokenFrom(outbox().sentMessages().getFirst())).andExpect(status().isNoContent());
			mockMvc.perform(post("/api/v1/auth/login").with(spaCsrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("SECRETARY"))
				.andExpect(jsonPath("$.clinic.id").value(clinic.getId().toString()));
		}

		@Test
		void requiresTheDoctorProfileForDoctors() throws Exception {
			expectFieldError(createDoctor(null), "doctor", "REQUIRED");
			expectFieldError(createDoctor("{\"primarySpecialty\": \"WIZARDRY\"}"), "doctor.primarySpecialty",
					"INVALID");
			expectFieldError(createDoctor("{\"primarySpecialty\": \"\"}"), "doctor.primarySpecialty", "REQUIRED");
		}

		@Test
		void onlyDoctorsAndSecretariesCanBeCreated() throws Exception {
			expectFieldError(create("OWNER", null), "role", "INVALID");
			expectFieldError(create("SECRETARY", "{\"primarySpecialty\": \"CARDIOLOGY\"}"), "doctor", "INVALID");
			assertThat(outbox().sentMessages()).isEmpty();
		}

		@Test
		void rejectsAnEmailThatAnyAccountAlreadyUses() throws Exception {
			for (String email : List.of(secretary.getEmail().toUpperCase(), otherClinicMember.getEmail())) {
				mockMvc.perform(withBody(post("/api/v1/staff"), """
						{"firstName": "Dup", "lastName": "Licate", "email": "%s", "role": "SECRETARY"}
						""".formatted(email)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
			}
			assertThat(outbox().sentMessages()).isEmpty();
		}

		@Test
		void validatesRequiredFields() throws Exception {
			mockMvc.perform(withBody(post("/api/v1/staff"), "{\"email\": \"not-an-email\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'firstName')].code").value(contains("REQUIRED")))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')].code").value(contains("INVALID_EMAIL")))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'role')].code").value(contains("REQUIRED")));
		}

		private ResultActions createDoctor(String doctorJson) throws Exception {
			return create("DOCTOR", doctorJson);
		}

		private ResultActions create(String role, String doctorJson) throws Exception {
			return mockMvc.perform(withBody(post("/api/v1/staff"), """
					{"firstName": "New", "lastName": "Member", "email": "%s", "role": "%s", "doctor": %s}
					""".formatted(TestClinicFixture.uniqueEmail("member"), role, doctorJson)));
		}

	}

	@Nested
	class Editing {

		@Test
		void updatesNameAndPhone() throws Exception {
			long loadedVersion = secretary.getVersion();
			mockMvc.perform(withBody(put("/api/v1/staff/{id}", secretary.getId()), """
					{"firstName": "Sarah", "lastName": "Adams-Lee", "email": "%s", "phone": "+33 1 00 00 00 00",
					 "version": %d}
					""".formatted(secretary.getEmail(), loadedVersion)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value("Sarah"))
				.andExpect(jsonPath("$.phone").value("+33 1 00 00 00 00"))
				.andExpect(jsonPath("$.version").value(loadedVersion + 1));

			fixture.flush();
			String metadata = jdbc.queryForObject(
					"select metadata::text from audit_event where action = 'STAFF_UPDATED' and entity_id = ?",
					String.class, secretary.getId());
			assertThat(metadata).contains("\"changedFields\": [\"firstName\", \"lastName\", \"phone\"]")
				.doesNotContain("Adams-Lee");
		}

		@Test
		void rejectsAStaleVersion() throws Exception {
			mockMvc.perform(withBody(put("/api/v1/staff/{id}", secretary.getId()), """
					{"firstName": "Sarah", "lastName": "Adams", "email": "%s", "version": %d}
					""".formatted(secretary.getEmail(), secretary.getVersion() + 5)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
		}

		@Test
		void activeMembersKeepTheirSignInEmail() throws Exception {
			mockMvc.perform(withBody(put("/api/v1/staff/{id}", secretary.getId()), """
					{"firstName": "Sara", "lastName": "Adams", "email": "%s", "version": %d}
					""".formatted(TestClinicFixture.uniqueEmail("changed"), secretary.getVersion())))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("EMAIL_CHANGE_NOT_ALLOWED"));
		}

		@Test
		void correctingAnInvitedMembersEmailSendsANewInvitationAndVoidsTheOldOne() throws Exception {
			AppUser invited = fixture.invitedUser(clinic, Role.SECRETARY, "Ian", "Vited");
			resendInvitation(invited.getId()).andExpect(status().isAccepted());
			String oldToken = tokenFrom(outbox().sentMessages().getFirst());
			outbox().clear();
			fixture.flush();

			String corrected = TestClinicFixture.uniqueEmail("corrected");
			mockMvc.perform(withBody(put("/api/v1/staff/{id}", invited.getId()), """
					{"firstName": "Ian", "lastName": "Vited", "email": "%s", "version": %d}
					""".formatted(corrected, invited.getVersion())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(corrected));

			assertThat(outbox().sentMessages()).singleElement().extracting(EmailMessage::to).isEqualTo(corrected);
			acceptInvitation(oldToken).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		}

	}

	@Nested
	class Status {

		@Test
		void deactivationSignsTheMemberOutAndBlocksSignIn() throws Exception {
			Cookie secretarySession = fixture.login(mockMvc, secretary);

			changeStatus(secretary.getId(), "DEACTIVATED")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DEACTIVATED"));

			mockMvc.perform(get("/api/v1/auth/me").cookie(secretarySession))
				.andExpect(status().isUnauthorized());
			login(secretary.getEmail()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
			assertThat(auditActions()).contains("STAFF_DEACTIVATED");
		}

		@Test
		void reactivationRestoresAccess() throws Exception {
			changeStatus(secretary.getId(), "DEACTIVATED").andExpect(status().isOk());
			changeStatus(secretary.getId(), "ACTIVE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
			login(secretary.getEmail()).andExpect(status().isOk());
		}

		@Test
		void reactivatedMembersWhoNeverSignedUpReturnToInvited() throws Exception {
			AppUser invited = fixture.invitedUser(clinic, Role.SECRETARY, "Ivy", "Pending");
			changeStatus(invited.getId(), "DEACTIVATED").andExpect(status().isOk());
			changeStatus(invited.getId(), "ACTIVE").andExpect(jsonPath("$.status").value("INVITED"));
		}

		@Test
		void deactivationRevokesAPendingInvitationLink() throws Exception {
			AppUser invited = fixture.invitedUser(clinic, Role.SECRETARY, "Ivo", "Revoked");
			resendInvitation(invited.getId()).andExpect(status().isAccepted());
			String token = tokenFrom(outbox().sentMessages().getFirst());

			changeStatus(invited.getId(), "DEACTIVATED").andExpect(status().isOk());
			changeStatus(invited.getId(), "ACTIVE").andExpect(status().isOk());

			acceptInvitation(token).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		}

		@Test
		void theOwnerCannotBeDeactivated() throws Exception {
			changeStatus(owner.getId(), "DEACTIVATED")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("OWNER_STATUS_LOCKED"));
		}

		@Test
		void doctorsWithUpcomingAppointmentsCannotBeDeactivated() throws Exception {
			appointmentFor(doctorProfile, Instant.now().plus(Duration.ofDays(2)));

			changeStatus(doctor.getId(), "DEACTIVATED")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("STAFF_HAS_UPCOMING_APPOINTMENTS"));
		}

		@Test
		void pastAppointmentsDoNotPreventDeactivation() throws Exception {
			appointmentFor(doctorProfile, Instant.now().minus(Duration.ofDays(2)));

			changeStatus(doctor.getId(), "DEACTIVATED").andExpect(status().isOk());
		}

		@Test
		void invitedIsNotARequestableStatus() throws Exception {
			expectFieldError(changeStatus(secretary.getId(), "INVITED"), "status", "INVALID");
		}

		private void appointmentFor(Doctor doctorProfile, Instant start) {
			Patient patient = patients.save(new Patient(clinic.getId(), "Pat", "Ient", LocalDate.of(1980, 1, 1),
					owner.getId()));
			patientDoctors.save(new PatientDoctor(clinic.getId(), patient.getId(), doctorProfile.getId(),
					DoctorRelationshipType.PRIMARY, owner.getId()));
			appointments.save(new Appointment(clinic.getId(), patient.getId(), doctorProfile.getId(), start,
					start.plus(Duration.ofMinutes(30)), AppointmentType.FOLLOW_UP, null, null, owner.getId()));
			fixture.flush();
		}

	}

	@Nested
	class Invitations {

		@Test
		void resendingIssuesANewLinkAndVoidsThePreviousOne() throws Exception {
			AppUser invited = fixture.invitedUser(clinic, Role.DOCTOR, "Rex", "Resend");
			resendInvitation(invited.getId()).andExpect(status().isAccepted());
			resendInvitation(invited.getId()).andExpect(status().isAccepted());
			List<EmailMessage> sent = outbox().sentMessages();
			assertThat(sent).hasSize(2);

			acceptInvitation(tokenFrom(sent.get(0))).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
			acceptInvitation(tokenFrom(sent.get(1))).andExpect(status().isNoContent());
		}

		@Test
		void onlyInvitedMembersCanReceiveAnInvitation() throws Exception {
			resendInvitation(secretary.getId())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("USER_NOT_INVITED"));
		}

		@Test
		void resendingIsRateLimited() throws Exception {
			AppUser invited = fixture.invitedUser(clinic, Role.SECRETARY, "Rita", "Limit");
			for (int i = 0; i < 5; i++) {
				resendInvitation(invited.getId()).andExpect(status().isAccepted());
			}
			resendInvitation(invited.getId())
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
		}

	}

	@Nested
	class PasswordResets {

		@Test
		void anOwnerEmailsAnActiveMemberAResetLink() throws Exception {
			outbox().clear();
			requestPasswordReset(secretary.getId()).andExpect(status().isAccepted());

			EmailMessage email = outbox().sentMessages().getFirst();
			assertThat(email.to()).isEqualTo(secretary.getEmail());
			assertThat(email.body()).contains("/reset-password#token=");
			String token = tokenFrom(email);
			mockMvc.perform(withJson(post("/api/v1/auth/reset-password"), """
					{"token": "%s", "newPassword": "replacement-passphrase", "confirmPassword": "replacement-passphrase"}
					""".formatted(token))).andExpect(status().isNoContent());
			assertThat(auditActions()).contains("PASSWORD_RESET_REQUESTED");
		}

		@Test
		void invitedAndDeactivatedMembersCannotBeReset() throws Exception {
			AppUser invited = fixture.invitedUser(clinic, Role.SECRETARY, "Ivy", "Pending");
			requestPasswordReset(invited.getId())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("USER_NOT_ACTIVE"));

			changeStatus(secretary.getId(), "DEACTIVATED").andExpect(status().isOk());
			requestPasswordReset(secretary.getId())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("USER_NOT_ACTIVE"));
		}

		@Test
		void aNewLinkReplacesThePreviousOne() throws Exception {
			requestPasswordReset(secretary.getId()).andExpect(status().isAccepted());
			requestPasswordReset(secretary.getId()).andExpect(status().isAccepted());
			List<EmailMessage> sent = outbox().sentMessages();
			assertThat(sent).hasSize(2);
			mockMvc.perform(withJson(post("/api/v1/auth/reset-password"), """
					{"token": "%s", "newPassword": "replacement-passphrase", "confirmPassword": "replacement-passphrase"}
					""".formatted(tokenFrom(sent.get(0))))).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
			mockMvc.perform(withJson(post("/api/v1/auth/reset-password"), """
					{"token": "%s", "newPassword": "replacement-passphrase", "confirmPassword": "replacement-passphrase"}
					""".formatted(tokenFrom(sent.get(1))))).andExpect(status().isNoContent());
		}

	}

	@Nested
	class Authorization {

		@Test
		void doctorsAndSecretariesCannotManageStaff() throws Exception {
			for (AppUser member : List.of(doctor, secretary)) {
				Cookie session = fixture.login(mockMvc, member);
				for (MockHttpServletRequestBuilder request : List.of(
						get("/api/v1/staff"),
						get("/api/v1/staff/{id}", member.getId()),
						withJson(post("/api/v1/staff"), """
								{"firstName": "X", "lastName": "Y", "email": "x@y.test", "role": "SECRETARY"}"""),
						withJson(put("/api/v1/staff/{id}", member.getId()), """
								{"firstName": "X", "lastName": "Y", "email": "%s", "version": 0}"""
								.formatted(member.getEmail())),
						withJson(patch("/api/v1/staff/{id}/status", owner.getId()), "{\"status\": \"DEACTIVATED\"}"),
						post("/api/v1/staff/{id}/invitation", member.getId()).with(spaCsrf()),
						post("/api/v1/staff/{id}/password-reset", member.getId()).with(spaCsrf()))) {
					mockMvc.perform(request.cookie(session))
						.andExpect(status().isForbidden())
						.andExpect(jsonPath("$.code").value("FORBIDDEN"));
				}
			}
		}

		@Test
		void anonymousCallersAreRejected() throws Exception {
			mockMvc.perform(get("/api/v1/staff"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		}

		@Test
		void membersOfOtherClinicsAreNotFound() throws Exception {
			UUID foreignId = otherClinicMember.getId();
			for (MockHttpServletRequestBuilder request : List.of(
					get("/api/v1/staff/{id}", foreignId),
					withJson(put("/api/v1/staff/{id}", foreignId), """
							{"firstName": "X", "lastName": "Y", "email": "%s", "version": %d}"""
							.formatted(otherClinicMember.getEmail(), otherClinicMember.getVersion())),
					withJson(patch("/api/v1/staff/{id}/status", foreignId), "{\"status\": \"DEACTIVATED\"}"),
					post("/api/v1/staff/{id}/invitation", foreignId).with(spaCsrf()),
					post("/api/v1/staff/{id}/password-reset", foreignId).with(spaCsrf()))) {
				mockMvc.perform(request.cookie(ownerSession))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("STAFF_NOT_FOUND"));
			}
			assertThat(jdbc.queryForObject("SELECT status FROM app_user WHERE id = ?", String.class, foreignId))
				.isEqualTo("ACTIVE");
		}

		@Test
		void changesRequireACsrfToken() throws Exception {
			mockMvc.perform(patch("/api/v1/staff/{id}/status", secretary.getId()).cookie(ownerSession)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\": \"DEACTIVATED\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
		}

	}

	private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String json) {
		return withJson(request, json).cookie(ownerSession);
	}

	private static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, String json) {
		return request.with(spaCsrf()).contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private ResultActions changeStatus(UUID id, String status) throws Exception {
		return mockMvc.perform(withBody(patch("/api/v1/staff/{id}/status", id), "{\"status\": \"%s\"}".formatted(status)));
	}

	private ResultActions resendInvitation(UUID id) throws Exception {
		return mockMvc.perform(post("/api/v1/staff/{id}/invitation", id).with(spaCsrf()).cookie(ownerSession));
	}

	private ResultActions requestPasswordReset(UUID id) throws Exception {
		return mockMvc.perform(post("/api/v1/staff/{id}/password-reset", id).with(spaCsrf()).cookie(ownerSession));
	}

	private ResultActions acceptInvitation(String token) throws Exception {
		return mockMvc.perform(withJson(post("/api/v1/auth/accept-invitation"), """
				{"token": "%s", "newPassword": "%s", "confirmPassword": "%s"}
				""".formatted(token, PASSWORD, PASSWORD)));
	}

	private ResultActions login(String email) throws Exception {
		return mockMvc.perform(withJson(post("/api/v1/auth/login"),
				"{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)));
	}

	private static void expectFieldError(ResultActions result, String field, String code) throws Exception {
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == '%s')].code".formatted(field)).value(contains(code)));
	}

	private List<String> auditActions() {
		fixture.flush();
		return jdbc.queryForList("SELECT action FROM audit_event WHERE clinic_id = ?", String.class, clinic.getId());
	}

	private static String tokenFrom(EmailMessage email) {
		Matcher matcher = TOKEN_IN_LINK.matcher(email.body());
		assertThat(matcher.find()).as("email contains a token link").isTrue();
		return matcher.group(1);
	}

	private OutboxEmailSender outbox() {
		return (OutboxEmailSender) emailSender;
	}

}
