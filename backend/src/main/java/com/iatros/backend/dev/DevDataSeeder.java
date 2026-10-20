package com.iatros.backend.dev;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.appointment.Appointment;
import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.AppointmentType;
import com.iatros.backend.auth.dto.PasswordRules;
import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.clinic.ClinicSettings;
import com.iatros.backend.clinic.ClinicSettingsRepository;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.doctor.SpecialtyRepository;
import com.iatros.backend.medicalrecord.Allergy;
import com.iatros.backend.medicalrecord.AllergyRepository;
import com.iatros.backend.medicalrecord.AllergySeverity;
import com.iatros.backend.medicalrecord.MedicalRecord;
import com.iatros.backend.medicalrecord.MedicalRecordRepository;
import com.iatros.backend.patient.DoctorRelationshipType;
import com.iatros.backend.patient.Gender;
import com.iatros.backend.patient.Patient;
import com.iatros.backend.patient.PatientDoctor;
import com.iatros.backend.patient.PatientDoctorRepository;
import com.iatros.backend.patient.PatientRepository;
import com.iatros.backend.schedule.DoctorSchedulePeriod;
import com.iatros.backend.schedule.DoctorSchedulePeriodRepository;
import com.iatros.backend.shared.i18n.Language;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.Role;

/**
 * Creates a fictional demo clinic for local development (decisions.md D-020). Runs only in the {@code dev}
 * profile, only when IATROS_DEMO_PASSWORD is set, and only once. All names and data are invented.
 */
@Component
@Profile("dev")
class DevDataSeeder implements ApplicationRunner {

	static final String OWNER_EMAIL = "owner@demo.iatros.test";
	static final String DOCTOR_EMAIL = "doctor@demo.iatros.test";
	static final String SECRETARY_EMAIL = "secretary@demo.iatros.test";

	private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
	private static final ZoneId CLINIC_ZONE = ZoneId.of("Europe/Athens");
	private static final List<LocalTime> SLOTS = List.of(LocalTime.of(9, 0), LocalTime.of(10, 0),
			LocalTime.of(11, 0), LocalTime.of(14, 30), LocalTime.of(16, 0));

	private final String demoPassword;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final ClinicRepository clinics;
	private final ClinicSettingsRepository clinicSettings;
	private final AppUserRepository users;
	private final SpecialtyRepository specialties;
	private final DoctorRepository doctors;
	private final PatientRepository patients;
	private final PatientDoctorRepository patientDoctors;
	private final MedicalRecordRepository medicalRecords;
	private final AllergyRepository allergies;
	private final DoctorSchedulePeriodRepository schedulePeriods;
	private final AppointmentRepository appointments;

	DevDataSeeder(@Value("${iatros.dev-seed.password:}") String demoPassword, PasswordEncoder passwordEncoder,
			Clock clock, ClinicRepository clinics, ClinicSettingsRepository clinicSettings, AppUserRepository users,
			SpecialtyRepository specialties, DoctorRepository doctors, PatientRepository patients,
			PatientDoctorRepository patientDoctors, MedicalRecordRepository medicalRecords,
			AllergyRepository allergies, DoctorSchedulePeriodRepository schedulePeriods,
			AppointmentRepository appointments) {
		this.demoPassword = demoPassword;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.clinics = clinics;
		this.clinicSettings = clinicSettings;
		this.users = users;
		this.specialties = specialties;
		this.doctors = doctors;
		this.patients = patients;
		this.patientDoctors = patientDoctors;
		this.medicalRecords = medicalRecords;
		this.allergies = allergies;
		this.schedulePeriods = schedulePeriods;
		this.appointments = appointments;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (users.existsByEmail(OWNER_EMAIL)) {
			return;
		}
		if (demoPassword.length() < PasswordRules.MIN_LENGTH) {
			log.info("Demo clinic not created: set IATROS_DEMO_PASSWORD (at least {} characters) to seed it.",
					PasswordRules.MIN_LENGTH);
			return;
		}
		String passwordHash = passwordEncoder.encode(demoPassword);

		Clinic clinic = clinics.save(new Clinic("IATROS Demo Clinic"));
		clinic.updateDetails("IATROS Demo Clinic", "contact@demo.iatros.test", "+30 210 000 0000",
				"1 Example Street, Athens");
		ClinicSettings settings = new ClinicSettings(clinic.getId(), CLINIC_ZONE);
		settings.update(CLINIC_ZONE, ClinicSettings.DEFAULT_APPOINTMENT_MINUTES, Language.EN, DayOfWeek.MONDAY);
		clinicSettings.save(settings);

		AppUser owner = activeUser(clinic, "Elena", "Markou", OWNER_EMAIL, Role.OWNER, passwordHash);
		AppUser doctorUser = activeUser(clinic, "Nikos", "Petridis", DOCTOR_EMAIL, Role.DOCTOR, passwordHash);
		activeUser(clinic, "Sofia", "Andreou", SECRETARY_EMAIL, Role.SECRETARY, passwordHash);

		Doctor ownerDoctor = doctors.save(new Doctor(clinic.getId(), owner.getId(),
				specialties.findByCode("GENERAL_PRACTICE").orElseThrow().getId()));
		Doctor cardiologist = doctors.save(new Doctor(clinic.getId(), doctorUser.getId(),
				specialties.findByCode("CARDIOLOGY").orElseThrow().getId()));
		List<Doctor> clinicDoctors = List.of(ownerDoctor, cardiologist);
		clinicDoctors.forEach(this::weekdaySchedule);

		List<List<Patient>> patientsByDoctor = List.of(new ArrayList<>(), new ArrayList<>());
		String[][] people = {
			{ "Maria", "Papadopoulou", "1978-02-11", "FEMALE" }, { "Giorgos", "Nikolaidis", "1965-07-23", "MALE" },
			{ "Anna", "Konstantinou", "1990-11-05", "FEMALE" }, { "Dimitris", "Alexiou", "1983-04-17", "MALE" },
			{ "Katerina", "Vlachou", "2001-09-30", "FEMALE" }, { "Petros", "Georgiou", "1957-01-08", "MALE" },
			{ "Ioanna", "Christou", "1995-06-14", "FEMALE" }, { "Stavros", "Ioannou", "1972-12-02", "MALE" },
			{ "Eleni", "Dimitriou", "1988-03-27", "FEMALE" }, { "Kostas", "Pappas", "1969-08-19", "MALE" },
		};
		for (int i = 0; i < people.length; i++) {
			String[] person = people[i];
			Doctor primary = clinicDoctors.get(i % 2);
			Patient patient = patients.save(new Patient(clinic.getId(), person[0], person[1],
					LocalDate.parse(person[2]), owner.getId()));
			patient.updateIdentity(person[0], person[1], LocalDate.parse(person[2]), Gender.valueOf(person[3]),
					owner.getId());
			patient.updateContact("+30 690 000 00" + String.format("%02d", i), null, null, null, null, owner.getId());
			patientDoctors.save(new PatientDoctor(clinic.getId(), patient.getId(), primary.getId(),
					DoctorRelationshipType.PRIMARY, owner.getId()));
			MedicalRecord record = medicalRecords.save(new MedicalRecord(clinic.getId(), patient.getId()));
			if (i % 3 == 0) {
				Allergy allergy = new Allergy(record.getId(), i % 2 == 0 ? "Penicillin" : "Pollen", owner.getId());
				allergy.update(allergy.getAllergen(), "Rash", AllergySeverity.MODERATE, null, owner.getId());
				allergies.save(allergy);
			}
			patientsByDoctor.get(i % 2).add(patient);
		}

		seedCurrentWeekAppointments(clinic.getId(), clinicDoctors, patientsByDoctor, owner.getId());
		log.info("Demo clinic created. Sign in as {}, {}, or {} with IATROS_DEMO_PASSWORD.", OWNER_EMAIL,
				DOCTOR_EMAIL, SECRETARY_EMAIL);
	}

	private AppUser activeUser(Clinic clinic, String firstName, String lastName, String email, Role role,
			String passwordHash) {
		AppUser user = new AppUser(clinic.getId(), firstName, lastName, email, role);
		user.activate(passwordHash);
		return users.save(user);
	}

	private void weekdaySchedule(Doctor doctor) {
		for (DayOfWeek day = DayOfWeek.MONDAY; day.getValue() <= DayOfWeek.FRIDAY.getValue(); day = day.plus(1)) {
			schedulePeriods.save(new DoctorSchedulePeriod(doctor.getId(), day, LocalTime.of(9, 0), LocalTime.of(13, 0)));
			schedulePeriods.save(new DoctorSchedulePeriod(doctor.getId(), day, LocalTime.of(14, 0), LocalTime.of(18, 0)));
		}
	}

	/** Past slots are completed (with an occasional no-show); future slots are scheduled or confirmed. */
	private void seedCurrentWeekAppointments(UUID clinicId, List<Doctor> clinicDoctors,
			List<List<Patient>> patientsByDoctor, UUID actorId) {
		Instant now = Instant.now(clock);
		LocalDate monday = LocalDate.now(clock.withZone(CLINIC_ZONE)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		int counter = 0;
		for (int dayOffset = 0; dayOffset < 5; dayOffset++) {
			LocalDate date = monday.plusDays(dayOffset);
			for (int d = 0; d < clinicDoctors.size(); d++) {
				List<Patient> doctorPatients = patientsByDoctor.get(d);
				for (LocalTime slot : SLOTS) {
					Patient patient = doctorPatients.get(counter % doctorPatients.size());
					Instant start = date.atTime(slot).atZone(CLINIC_ZONE).toInstant();
					Appointment appointment = new Appointment(clinicId, patient.getId(), clinicDoctors.get(d).getId(),
							start, start.plus(Duration.ofMinutes(30)),
							counter % 4 == 0 ? AppointmentType.NEW_PATIENT : AppointmentType.FOLLOW_UP, null, null,
							actorId);
					if (start.isBefore(now)) {
						if (counter % 9 == 0) {
							appointment.changeStatus(AppointmentStatus.NO_SHOW, start, actorId);
						}
						else {
							complete(appointment, start, actorId);
						}
					}
					else if (counter % 2 == 0) {
						appointment.changeStatus(AppointmentStatus.CONFIRMED, now, actorId);
					}
					appointments.save(appointment);
					counter++;
				}
			}
		}
	}

	private static void complete(Appointment appointment, Instant start, UUID actorId) {
		appointment.changeStatus(AppointmentStatus.WAITING, start, actorId);
		appointment.changeStatus(AppointmentStatus.IN_CONSULTATION, start, actorId);
		appointment.changeStatus(AppointmentStatus.COMPLETED, start.plus(Duration.ofMinutes(25)), actorId);
	}

}
