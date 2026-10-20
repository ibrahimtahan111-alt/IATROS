package com.iatros.backend.support;

import static com.iatros.backend.support.SpaCsrf.spaCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneId;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.clinic.ClinicSettings;
import com.iatros.backend.clinic.ClinicSettingsRepository;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.doctor.SpecialtyRepository;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.Role;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;

/** Builds fictional clinics and accounts for API tests and signs them in through the real login endpoint. */
@Component
public class TestClinicFixture {

	public static final String PASSWORD = "correct-horse-battery";
	public static final String SESSION_COOKIE = "IATROS_SESSION";

	private final ClinicRepository clinics;
	private final ClinicSettingsRepository clinicSettings;
	private final AppUserRepository users;
	private final DoctorRepository doctors;
	private final SpecialtyRepository specialties;
	private final PasswordEncoder passwordEncoder;
	private final EntityManager entityManager;

	TestClinicFixture(ClinicRepository clinics, ClinicSettingsRepository clinicSettings, AppUserRepository users,
			DoctorRepository doctors, SpecialtyRepository specialties, PasswordEncoder passwordEncoder,
			EntityManager entityManager) {
		this.clinics = clinics;
		this.clinicSettings = clinicSettings;
		this.users = users;
		this.doctors = doctors;
		this.specialties = specialties;
		this.passwordEncoder = passwordEncoder;
		this.entityManager = entityManager;
	}

	public Clinic clinic(String name) {
		Clinic clinic = clinics.save(new Clinic(name));
		clinicSettings.save(new ClinicSettings(clinic.getId(), ZoneId.of("Europe/Paris")));
		return clinic;
	}

	public AppUser activeUser(Clinic clinic, Role role, String firstName, String lastName) {
		AppUser user = new AppUser(clinic.getId(), firstName, lastName, uniqueEmail(role.name().toLowerCase()), role);
		user.activate(passwordEncoder.encode(PASSWORD));
		return users.save(user);
	}

	public AppUser invitedUser(Clinic clinic, Role role, String firstName, String lastName) {
		return users.save(new AppUser(clinic.getId(), firstName, lastName, uniqueEmail("invited"), role));
	}

	public Doctor doctorProfile(Clinic clinic, AppUser user, String specialtyCode) {
		return doctors.save(new Doctor(clinic.getId(), user.getId(),
				specialties.findByCode(specialtyCode).orElseThrow().getId()));
	}

	public Cookie login(MockMvc mockMvc, AppUser user) throws Exception {
		flush();
		return mockMvc.perform(post("/api/v1/auth/login").with(spaCsrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getEmail(), PASSWORD)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getCookie(SESSION_COOKIE);
	}

	public void flush() {
		entityManager.flush();
	}

	public void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	public static String uniqueEmail(String prefix) {
		return prefix + "-" + UUID.randomUUID() + "@fixture.test";
	}

}
