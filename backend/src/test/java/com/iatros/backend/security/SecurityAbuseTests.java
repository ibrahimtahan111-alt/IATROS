package com.iatros.backend.security;

import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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
import com.iatros.backend.support.TestClinicFixture;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Abuse matrix (roadmap.md Phase 13, security.md §15, decisions.md D-045). Every API route that addresses a resource by ID is discovered
 * from the running application and called with real IDs of clinic A by users who must not reach them. A new route
 * that takes a body must be given one in {@link #attackBodies}, so no endpoint escapes the matrix silently.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityAbuseTests {

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
	private static final String MARKER = "Quillfeather";
	private static final byte[] PDF = "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
	private static final Pattern PATH_VARIABLE = Pattern.compile("\\{(\\w+)}");
	private static final Set<String> CLINICAL = Set.of("/medical-record", "/consultations", "/documents");

	@Autowired private MockMvc mockMvc;
	@Autowired private TestClinicFixture fixture;
	@Autowired private RateLimiter rateLimiter;
	@Autowired private PatientRepository patients;
	@Autowired private PatientDoctorRepository patientDoctors;
	@Autowired private MedicalRecordRepository records;
	@Autowired private DoctorSchedulePeriodRepository periods;
	@Autowired private JdbcTemplate jdbc;
	@Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;

	private Clinic clinic;
	private Doctor cardiologist;
	private Doctor otherClinicDoctor;
	private Patient otherClinicPatient;
	private Cookie ownerSession;
	private Cookie secretarySession;
	private Cookie unlinkedDoctorSession;
	private Cookie otherClinicOwnerSession;
	private LocalDate day;
	private final Map<String, String> ids = new HashMap<>();

	private record Route(HttpMethod method, String pattern, boolean takesBody, boolean multipart) {

		String key() {
			return method.name() + " " + pattern;
		}

		boolean clinical() {
			return CLINICAL.stream().anyMatch(pattern::contains);
		}
	}

	private record Outcome(Route route, int status, String body) {

		@Override
		public String toString() {
			return route.key() + " -> " + status;
		}
	}

	@BeforeEach
	void setUp() throws Exception {
		rateLimiter.reset();
		day = LocalDate.now(PARIS).plusDays(2);

		clinic = fixture.clinic("Target Clinic");
		AppUser owner = fixture.activeUser(clinic, Role.OWNER, "Olivia", "Owner");
		AppUser secretary = fixture.activeUser(clinic, Role.SECRETARY, "Sara", "Adams");
		AppUser daniel = fixture.activeUser(clinic, Role.DOCTOR, "Daniel", "Brown");
		AppUser chloe = fixture.activeUser(clinic, Role.DOCTOR, "Chloe", "Carter");
		cardiologist = fixture.doctorProfile(clinic, daniel, "CARDIOLOGY");
		fixture.doctorProfile(clinic, chloe, "DERMATOLOGY");
		workEveryDay(cardiologist);
		Patient patient = patient(clinic, "Zephyrine", MARKER, cardiologist);

		Clinic otherClinic = fixture.clinic("Attacker Clinic");
		AppUser mallory = fixture.activeUser(otherClinic, Role.OWNER, "Mallory", "Moss");
		otherClinicDoctor = fixture.doctorProfile(otherClinic, mallory, "CARDIOLOGY");
		workEveryDay(otherClinicDoctor);
		otherClinicPatient = patient(otherClinic, "Trent", "Example", otherClinicDoctor);

		ownerSession = fixture.login(mockMvc, owner);
		secretarySession = fixture.login(mockMvc, secretary);
		unlinkedDoctorSession = fixture.login(mockMvc, chloe);
		Cookie cardiologistSession = fixture.login(mockMvc, daniel);
		otherClinicOwnerSession = fixture.login(mockMvc, mallory);

		ids.put("patient", patient.getId().toString());
		ids.put("staff", secretary.getId().toString());
		ids.put("doctorId", cardiologist.getId().toString());
		String record = "/api/v1/patients/" + patient.getId() + "/medical-record";
		ids.put("allergyId", create(cardiologistSession, record + "/allergies", """
				{"allergen": "%s pollen", "severity": "SEVERE"}""".formatted(MARKER)));
		ids.put("conditionId", create(cardiologistSession, record + "/conditions", """
				{"name": "%s syndrome", "status": "ACTIVE"}""".formatted(MARKER)));
		ids.put("medicationId", create(cardiologistSession, record + "/medications", """
				{"name": "%s tablets", "active": true}""".formatted(MARKER)));
		ids.put("entryId", create(cardiologistSession, record + "/history", """
				{"description": "%s surgery"}""".formatted(MARKER)));
		ids.put("noteId", create(cardiologistSession, record + "/notes", """
				{"content": "%s note"}""".formatted(MARKER)));
		ids.put("consultationId", create(cardiologistSession, "/api/v1/patients/" + patient.getId() + "/consultations",
				"""
				{"consultedAt": "%s", "reason": "%s review"}""".formatted(Instant.now().minus(1, ChronoUnit.DAYS),
						MARKER)));
		ids.put("documentId", JsonPath.read(mockMvc.perform(multipart("/api/v1/patients/{id}/documents", patient.getId())
			.file(new MockMultipartFile("file", MARKER + ".pdf", "application/pdf", PDF))
			.with(spaCsrf()).cookie(cardiologistSession))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));
		ids.put("appointmentId", create(cardiologistSession, "/api/v1/appointments", appointment(patient.getId(),
				cardiologist.getId(), "10:00")));
		ids.put("exceptionId", create(ownerSession, "/api/v1/doctors/" + cardiologist.getId() + "/schedule/exceptions",
				"""
				{"date": "%s", "type": "UNAVAILABLE", "reason": "%s leave"}""".formatted(day.plusDays(20), MARKER)));
		fixture.flush();
	}

	@Test
	void everyIdRouteIsCoveredByTheMatrix() {
		List<Route> routes = idRoutes();
		Map<String, String> bodies = attackBodies();

		assertThat(routes).extracting(Route::key).contains("GET /api/v1/patients/{id}",
				"GET /api/v1/documents/{documentId}/content", "DELETE /api/v1/doctors/{doctorId}/schedule/exceptions/{exceptionId}");
		assertThat(routes.stream().filter(route -> route.takesBody() && !bodies.containsKey(route.key())))
			.as("routes with a request body but no attack body")
			.isEmpty();
	}

	@Test
	void theMatrixReachesRealResourcesForTheirOwnClinic() throws Exception {
		List<Outcome> reads = attack(ownerSession, route -> route.method() == HttpMethod.GET);

		assertThat(reads).hasSizeGreaterThanOrEqualTo(15);
		assertThat(reads.stream().filter(outcome -> outcome.status() != 200)).isEmpty();
	}

	@Test
	void anotherClinicsOwnerFindsNothingByIdAndChangesNothing() throws Exception {
		Map<String, String> before = snapshot();
		long auditEvents = auditEventsOfTheTargetClinic();

		List<Outcome> outcomes = attack(otherClinicOwnerSession, route -> true);
		List<Outcome> failures = outcomes.stream()
			.filter(outcome -> outcome.status() != 404 || leaks(outcome.body()))
			.toList();

		assertThat(outcomes).hasSizeGreaterThanOrEqualTo(50);
		assertThat(failures).as("cross-clinic requests must answer 404 without data").isEmpty();
		assertThat(auditEventsOfTheTargetClinic()).isEqualTo(auditEvents);
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void secretariesAreForbiddenFromEveryClinicalRoute() throws Exception {
		Map<String, String> before = snapshot();

		List<Outcome> outcomes = attack(secretarySession, Route::clinical);
		assertThat(outcomes).hasSizeGreaterThan(20);
		assertThat(outcomes.stream().filter(outcome -> outcome.status() != 403 || leaks(outcome.body())))
			.as("clinical routes must answer 403 to secretaries")
			.isEmpty();
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void unlinkedDoctorsCannotReachAnotherDoctorsPatientOrChangeTheirResources() throws Exception {
		Map<String, String> before = snapshot();

		// A colleague's schedule and working hours are clinic-wide operational data (roles-and-permissions.md).
		Predicate<Route> protectedRoutes = route -> !(route.method() == HttpMethod.GET
				&& route.pattern().startsWith("/api/v1/doctors/"));
		List<Outcome> failures = attack(unlinkedDoctorSession, protectedRoutes).stream()
			.filter(outcome -> (outcome.status() != 403 && outcome.status() != 404) || leaks(outcome.body()))
			.toList();

		assertThat(failures).as("unlinked doctors must get 403 or 404 without data").isEmpty();
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void anotherClinicsIdsInBodiesAndFiltersAreRejected() throws Exception {
		String patientId = ids.get("patient");
		UUID doctorId = cardiologist.getId();
		String ownPatient = "/api/v1/patients/" + otherClinicPatient.getId();
		String ownAppointment = create(otherClinicOwnerSession, "/api/v1/appointments",
				appointment(otherClinicPatient.getId(), otherClinicDoctor.getId(), "11:00"));
		long auditEvents = auditEventsOfTheTargetClinic();

		List<AbstractMockHttpServletRequestBuilder<?>> writes = List.of(
				json(post("/api/v1/appointments"), appointment(UUID.fromString(patientId), otherClinicDoctor.getId(),
						"12:00")),
				json(post("/api/v1/appointments"), appointment(otherClinicPatient.getId(), doctorId, "12:00")),
				json(patch("/api/v1/appointments/{id}/schedule", ownAppointment), """
						{"doctorId": "%s", "startAt": "%s", "endAt": "%s", "version": 0}"""
					.formatted(doctorId, at("12:00"), at("12:30"))),
				json(post("/api/v1/patients"), """
						{"firstName": "Eve", "lastName": "Example", "dateOfBirth": "1990-01-01",
						 "primaryDoctorId": "%s"}""".formatted(doctorId)),
				json(patch(ownPatient + "/primary-doctor"), "{\"doctorId\": \"%s\"}".formatted(doctorId)),
				json(post(ownPatient + "/doctors"), "{\"doctorId\": \"%s\"}".formatted(doctorId)),
				json(post(ownPatient + "/consultations"), """
						{"consultedAt": "%s", "reason": "Review", "appointmentId": "%s"}"""
					.formatted(Instant.now().minus(1, ChronoUnit.HOURS), ids.get("appointmentId"))),
				multipart(ownPatient + "/documents").file(new MockMultipartFile("file", "scan.pdf", "application/pdf",
						PDF)).param("consultationId", ids.get("consultationId")));
		for (AbstractMockHttpServletRequestBuilder<?> write : writes) {
			MvcResult result = mockMvc.perform(write.with(spaCsrf()).cookie(otherClinicOwnerSession)).andReturn();
			assertThat(result.getResponse().getStatus()).as(result.getRequest().getRequestURI()).isBetween(400, 499);
			assertThat(leaks(result.getResponse().getContentAsString())).isFalse();
		}

		List<String> filters = List.of("/api/v1/patients?primaryDoctorId=" + doctorId,
				"/api/v1/appointments?doctorId=" + doctorId, "/api/v1/appointments?patientId=" + patientId,
				"/api/v1/appointments/availability?doctorId=%s&date=%s".formatted(doctorId, day),
				"/api/v1/calendar/events?from=%s&to=%s&doctorId=%s".formatted(day, day.plusDays(1), doctorId),
				"/api/v1/activity?doctorId=" + doctorId, "/api/v1/kpis/doctors/" + doctorId,
				"/api/v1/patients/%s/consultations?doctorId=%s".formatted(otherClinicPatient.getId(), doctorId),
				"/api/v1/patients/%s/documents?consultationId=%s".formatted(otherClinicPatient.getId(),
						ids.get("consultationId")));
		for (String filter : filters) {
			MvcResult result = mockMvc.perform(get(filter).cookie(otherClinicOwnerSession)).andReturn();
			String body = result.getResponse().getContentAsString();
			assertThat(leaks(body) || body.contains(patientId) || body.contains(ids.get("appointmentId")))
				.as(filter).isFalse();
		}
		assertThat(auditEventsOfTheTargetClinic()).isEqualTo(auditEvents);
	}

	private List<Outcome> attack(Cookie session, Predicate<Route> include) throws Exception {
		Map<String, String> bodies = attackBodies();
		List<Outcome> outcomes = new ArrayList<>();
		for (Route route : idRoutes()) {
			if (!include.test(route)) {
				continue;
			}
			String uri = expand(route.pattern());
			AbstractMockHttpServletRequestBuilder<?> builder;
			if (route.multipart()) {
				builder = multipart(uri).file(new MockMultipartFile("file", "scan.pdf", "application/pdf", PDF));
			}
			else if (route.takesBody()) {
				builder = json(request(route.method(), uri), bodies.get(route.key()));
			}
			else {
				builder = request(route.method(), uri);
			}
			if (route.pattern().endsWith("/working-hours")) {
				builder.param("from", day.toString()).param("to", day.plusDays(6).toString());
			}
			MvcResult result = mockMvc.perform(builder.with(spaCsrf()).cookie(session)).andReturn();
			outcomes.add(new Outcome(route, result.getResponse().getStatus(),
					result.getResponse().getContentAsString()));
		}
		return outcomes;
	}

	private List<Route> idRoutes() {
		List<Route> routes = new ArrayList<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping : handlerMapping.getHandlerMethods().entrySet()) {
			HandlerMethod handler = mapping.getValue();
			boolean body = Arrays.stream(handler.getMethodParameters())
				.anyMatch(parameter -> parameter.hasParameterAnnotation(RequestBody.class));
			boolean multipart = Arrays.stream(handler.getMethodParameters())
				.anyMatch(parameter -> MultipartFile.class.isAssignableFrom(parameter.getParameterType()));
			for (String pattern : mapping.getKey().getPatternValues()) {
				if (!pattern.startsWith("/api/v1/") || !pattern.contains("{")) {
					continue;
				}
				mapping.getKey().getMethodsCondition().getMethods().forEach(method ->
						routes.add(new Route(HttpMethod.valueOf(method.name()), pattern, body, multipart)));
			}
		}
		routes.sort(Comparator.comparing(Route::key));
		return routes;
	}

	private String expand(String pattern) {
		Matcher matcher = PATH_VARIABLE.matcher(pattern);
		StringBuilder uri = new StringBuilder();
		while (matcher.find()) {
			String name = matcher.group(1);
			String key = switch (name) {
				case "id" -> pattern.startsWith("/api/v1/staff/") ? "staff" : "patient";
				case "patientId" -> "patient";
				default -> name;
			};
			String value = ids.get(key);
			assertThat(value).as("no test ID for {%s} in %s", name, pattern).isNotNull();
			matcher.appendReplacement(uri, value);
		}
		matcher.appendTail(uri);
		return uri.toString();
	}

	/** Valid bodies, so that a refusal comes from authorization rather than from validation. */
	private Map<String, String> attackBodies() {
		String record = "/api/v1/patients/{patientId}/medical-record";
		Map<String, String> bodies = new LinkedHashMap<>();
		bodies.put("PUT /api/v1/patients/{id}", """
				{"firstName": "Eve", "lastName": "Example", "dateOfBirth": "1990-01-01", "version": 0}""");
		bodies.put("PATCH /api/v1/patients/{id}/status", "{\"status\": \"ARCHIVED\"}");
		bodies.put("PATCH /api/v1/patients/{id}/primary-doctor", "{\"doctorId\": \"%s\"}".formatted(otherClinicDoctor.getId()));
		bodies.put("POST /api/v1/patients/{id}/doctors", "{\"doctorId\": \"%s\"}".formatted(otherClinicDoctor.getId()));
		bodies.put("PUT /api/v1/staff/{id}", """
				{"firstName": "Eve", "lastName": "Example", "email": "eve@fixture.test", "version": 0}""");
		bodies.put("PATCH /api/v1/staff/{id}/status", "{\"status\": \"DEACTIVATED\"}");
		bodies.put("PUT /api/v1/doctors/{doctorId}", "{\"primarySpecialty\": \"DERMATOLOGY\", \"version\": 0}");
		bodies.put("PUT /api/v1/doctors/{doctorId}/schedule", "{\"periods\": [], \"version\": 0}");
		bodies.put("POST /api/v1/doctors/{doctorId}/schedule/exceptions",
				"{\"date\": \"%s\", \"type\": \"UNAVAILABLE\"}".formatted(day.plusDays(21)));
		bodies.put("PUT /api/v1/appointments/{appointmentId}", "{\"type\": \"URGENT\", \"version\": 0}");
		bodies.put("PATCH /api/v1/appointments/{appointmentId}/schedule", """
				{"doctorId": "%s", "startAt": "%s", "endAt": "%s", "version": 0}"""
			.formatted(cardiologist.getId(), at("15:00"), at("15:30")));
		bodies.put("PATCH /api/v1/appointments/{appointmentId}/status", "{\"status\": \"CONFIRMED\", \"version\": 0}");
		bodies.put("POST /api/v1/appointments/{appointmentId}/cancel", "{\"version\": 0}");
		bodies.put("POST " + record + "/allergies", "{\"allergen\": \"Latex\"}");
		bodies.put("PUT " + record + "/allergies/{allergyId}", "{\"allergen\": \"Latex\", \"version\": 0}");
		bodies.put("POST " + record + "/conditions", "{\"name\": \"Asthma\", \"status\": \"ACTIVE\"}");
		bodies.put("PUT " + record + "/conditions/{conditionId}",
				"{\"name\": \"Asthma\", \"status\": \"RESOLVED\", \"version\": 0}");
		bodies.put("POST " + record + "/medications", "{\"name\": \"Aspirin\", \"active\": true}");
		bodies.put("PUT " + record + "/medications/{medicationId}",
				"{\"name\": \"Aspirin\", \"active\": false, \"version\": 0}");
		bodies.put("POST " + record + "/history", "{\"description\": \"Fracture\"}");
		bodies.put("PUT " + record + "/history/{entryId}", "{\"description\": \"Fracture\", \"version\": 0}");
		bodies.put("POST " + record + "/notes", "{\"content\": \"Overwritten\"}");
		bodies.put("PUT " + record + "/notes/{noteId}", "{\"content\": \"Overwritten\", \"version\": 0}");
		String consultation = "{\"consultedAt\": \"%s\", \"reason\": \"Review\"%s}";
		Instant consultedAt = Instant.now().minus(2, ChronoUnit.HOURS);
		bodies.put("POST /api/v1/patients/{patientId}/consultations", consultation.formatted(consultedAt, ""));
		bodies.put("PUT /api/v1/consultations/{consultationId}", consultation.formatted(consultedAt, ", \"version\": 0"));
		return bodies;
	}

	/** What clinic A's owner sees of the attacked resources; any successful attack would change it. */
	private Map<String, String> snapshot() throws Exception {
		Map<String, String> views = new LinkedHashMap<>();
		String patient = "/api/v1/patients/" + ids.get("patient");
		String doctor = "/api/v1/doctors/" + ids.get("doctorId");
		for (String uri : List.of(patient, patient + "/medical-record", patient + "/medical-record/notes",
				patient + "/consultations", patient + "/documents", "/api/v1/appointments/" + ids.get("appointmentId"),
				"/api/v1/consultations/" + ids.get("consultationId"), "/api/v1/documents/" + ids.get("documentId"),
				doctor, doctor + "/schedule", doctor + "/schedule/exceptions", "/api/v1/staff/" + ids.get("staff"))) {
			views.put(uri, mockMvc.perform(get(uri).cookie(ownerSession))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		}
		return views;
	}

	private boolean leaks(String body) {
		return body.contains(MARKER) || body.contains("Zephyrine");
	}

	private long auditEventsOfTheTargetClinic() {
		return jdbc.queryForObject("select count(*) from audit_event where clinic_id = ?", Long.class, clinic.getId());
	}

	private String create(Cookie session, String uri, String body) throws Exception {
		return JsonPath.read(mockMvc.perform(json(post(uri), body).with(spaCsrf()).cookie(session))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private String appointment(UUID patientId, UUID doctorId, String start) {
		return """
				{"patientId": "%s", "doctorId": "%s", "startAt": "%s", "endAt": "%s", "type": "ROUTINE"}"""
			.formatted(patientId, doctorId, at(start), at(LocalTime.parse(start).plusMinutes(30).toString()));
	}

	private Instant at(String time) {
		return day.atTime(LocalTime.parse(time)).atZone(PARIS).toInstant();
	}

	private void workEveryDay(Doctor doctor) {
		for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
			periods.save(new DoctorSchedulePeriod(doctor.getId(), dayOfWeek, LocalTime.of(8, 0), LocalTime.of(18, 0)));
		}
	}

	private Patient patient(Clinic owningClinic, String firstName, String lastName, Doctor primary) {
		Patient patient = patients.save(new Patient(owningClinic.getId(), firstName, lastName,
				LocalDate.of(1980, 1, 1), primary.getUserId()));
		records.save(new MedicalRecord(owningClinic.getId(), patient.getId()));
		patientDoctors.save(new PatientDoctor(owningClinic.getId(), patient.getId(), primary.getId(),
				DoctorRelationshipType.PRIMARY, primary.getUserId()));
		fixture.flush();
		return patient;
	}

}
