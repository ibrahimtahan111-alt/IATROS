package com.iatros.backend.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.appointment.Appointment;
import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.AppointmentType;
import com.iatros.backend.audit.AuditEvent;
import com.iatros.backend.audit.AuditEventRepository;
import com.iatros.backend.auth.TokenPurpose;
import com.iatros.backend.auth.UserToken;
import com.iatros.backend.auth.UserTokenRepository;
import com.iatros.backend.clinic.Clinic;
import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.clinic.ClinicSettings;
import com.iatros.backend.clinic.ClinicSettingsRepository;
import com.iatros.backend.consultation.Consultation;
import com.iatros.backend.consultation.ConsultationRepository;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.doctor.SpecialtyRepository;
import com.iatros.backend.document.MedicalDocument;
import com.iatros.backend.document.MedicalDocumentRepository;
import com.iatros.backend.medicalrecord.Allergy;
import com.iatros.backend.medicalrecord.AllergyRepository;
import com.iatros.backend.medicalrecord.AllergySeverity;
import com.iatros.backend.medicalrecord.MedicalNote;
import com.iatros.backend.medicalrecord.MedicalNoteRepository;
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
import com.iatros.backend.schedule.ScheduleException;
import com.iatros.backend.schedule.ScheduleExceptionRepository;
import com.iatros.backend.schedule.ScheduleExceptionType;
import com.iatros.backend.shared.error.DatabaseConstraintCodes;
import com.iatros.backend.shared.i18n.Language;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.Role;
import com.iatros.backend.user.UserStatus;

import jakarta.persistence.EntityManager;

/**
 * Round-trips every entity through PostgreSQL to prove the JPA mappings match the Flyway schema,
 * including citext, smallint, jsonb, bytea, and the database constraints the services rely on.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersistenceMappingTests {

	private static final Instant NINE = Instant.parse("2027-04-12T09:00:00Z");

	@Autowired private EntityManager entityManager;
	@Autowired private ClinicRepository clinics;
	@Autowired private ClinicSettingsRepository clinicSettings;
	@Autowired private AppUserRepository users;
	@Autowired private SpecialtyRepository specialties;
	@Autowired private DoctorRepository doctors;
	@Autowired private PatientRepository patients;
	@Autowired private PatientDoctorRepository patientDoctors;
	@Autowired private MedicalRecordRepository medicalRecords;
	@Autowired private AllergyRepository allergies;
	@Autowired private MedicalNoteRepository notes;
	@Autowired private DoctorSchedulePeriodRepository schedulePeriods;
	@Autowired private ScheduleExceptionRepository scheduleExceptions;
	@Autowired private AppointmentRepository appointments;
	@Autowired private ConsultationRepository consultations;
	@Autowired private MedicalDocumentRepository documents;
	@Autowired private AuditEventRepository auditEvents;
	@Autowired private UserTokenRepository userTokens;

	private Clinic clinic;
	private AppUser owner;
	private Doctor doctor;
	private Patient patient;
	private MedicalRecord record;

	@BeforeEach
	void setUp() {
		clinic = clinics.save(new Clinic("Clinique Test"));
		owner = users.save(new AppUser(clinic.getId(), "Olivia", "Owner", "Owner@Clinic.Test", Role.OWNER));
		AppUser doctorUser = users.save(new AppUser(clinic.getId(), "Daniel", "Doctor", "doctor@clinic.test", Role.DOCTOR));
		doctor = doctors.save(new Doctor(clinic.getId(), doctorUser.getId(),
				specialties.findByCode("CARDIOLOGY").orElseThrow().getId()));
		patient = patients.save(new Patient(clinic.getId(), "Paula", "Patient", LocalDate.of(1985, 3, 14), owner.getId()));
		record = medicalRecords.save(new MedicalRecord(clinic.getId(), patient.getId()));
		flushAndClear();
	}

	@Test
	void clinicAndSettingsRoundTrip() {
		ClinicSettings settings = new ClinicSettings(clinic.getId(), ZoneId.of("Europe/Paris"));
		settings.update(ZoneId.of("Europe/Paris"), 20, Language.FR, DayOfWeek.MONDAY);
		clinicSettings.save(settings);
		clinics.findById(clinic.getId()).orElseThrow()
			.updateDetails("Clinique Test", "Contact@Clinic.Test", null, null);
		flushAndClear();

		ClinicSettings loaded = clinicSettings.findById(clinic.getId()).orElseThrow();
		assertThat(loaded.getTimeZone()).isEqualTo(ZoneId.of("Europe/Paris"));
		assertThat(loaded.getDefaultAppointmentMinutes()).isEqualTo(20);
		assertThat(loaded.getDefaultLanguage()).isEqualTo(Language.FR);
		assertThat(loaded.getWeekStartDay()).isEqualTo(DayOfWeek.MONDAY);
		Clinic reloaded = clinics.findById(clinic.getId()).orElseThrow();
		assertThat(reloaded.getEmail()).isEqualTo("Contact@Clinic.Test");
		assertThat(reloaded.getVersion()).isEqualTo(1);
	}

	@Test
	void userEmailLookupIsCaseInsensitive() {
		assertThat(users.findByEmail("owner@clinic.test")).get().extracting(AppUser::getId).isEqualTo(owner.getId());
		assertThat(users.existsByEmail("OWNER@CLINIC.TEST")).isTrue();
	}

	@Test
	void duplicateEmailIgnoringCaseIsRejected() {
		users.save(new AppUser(clinic.getId(), "Other", "Person", "OWNER@clinic.test", Role.SECRETARY));

		assertThatThrownBy(() -> users.flush()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void userActivationAndDeactivationPersist() {
		AppUser user = users.findById(owner.getId()).orElseThrow();
		user.activate("{argon2}hash");
		user.deactivate(NINE);
		flushAndClear();

		AppUser loaded = users.findById(owner.getId()).orElseThrow();
		assertThat(loaded.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
		assertThat(loaded.getDeactivatedAt()).isEqualTo(NINE);
	}

	@Test
	void clinicScopedLookupDoesNotFindOtherClinicsRows() {
		Clinic other = clinics.save(new Clinic("Other clinic"));
		flushAndClear();

		assertThat(patients.findByIdAndClinicId(patient.getId(), clinic.getId())).isPresent();
		assertThat(patients.findByIdAndClinicId(patient.getId(), other.getId())).isEmpty();
		assertThat(doctors.findByIdAndClinicId(doctor.getId(), other.getId())).isEmpty();
	}

	@Test
	void patientDetailsAndDoctorRelationshipRoundTrip() {
		Patient loaded = patients.findById(patient.getId()).orElseThrow();
		loaded.updateIdentity("Paula", "Patient", LocalDate.of(1985, 3, 14), Gender.FEMALE, owner.getId());
		patientDoctors.save(new PatientDoctor(clinic.getId(), patient.getId(), doctor.getId(),
				DoctorRelationshipType.PRIMARY, owner.getId()));
		flushAndClear();

		assertThat(patients.findById(patient.getId()).orElseThrow().getGender()).isEqualTo(Gender.FEMALE);
		assertThat(patientDoctors.existsByPatientIdAndDoctorId(patient.getId(), doctor.getId())).isTrue();
		assertThat(patientDoctors.findByPatientIdAndRelationshipType(patient.getId(), DoctorRelationshipType.PRIMARY))
			.isPresent();
	}

	@Test
	void softDeletedClinicalEntriesAreHiddenFromNormalReads() {
		Allergy kept = new Allergy(record.getClinicId(), record.getId(), "Penicillin", owner.getId());
		kept.update("Penicillin", "Rash", AllergySeverity.MODERATE, null, owner.getId());
		Allergy removed = new Allergy(record.getClinicId(), record.getId(), "Latex", owner.getId());
		allergies.saveAll(List.of(kept, removed));
		flushAndClear();

		Allergy toDelete = allergies.findById(removed.getId()).orElseThrow();
		toDelete.softDelete(NINE, owner.getId());
		flushAndClear();

		assertThat(allergies.findAllByMedicalRecordIdOrderByCreatedAtDesc(record.getId()))
			.extracting(Allergy::getAllergen)
			.containsExactly("Penicillin");
		assertThat(allergies.findById(removed.getId())).isEmpty();
	}

	@Test
	void medicalNotesArePageable() {
		notes.save(new MedicalNote(record.getClinicId(), record.getId(), "First note", owner.getId()));
		notes.save(new MedicalNote(record.getClinicId(), record.getId(), "Second note", owner.getId()));
		flushAndClear();

		assertThat(notes.findAllByMedicalRecordId(record.getId(), PageRequest.of(0, 1)).getTotalElements()).isEqualTo(2);
	}

	@Test
	void schedulesRoundTripAndOverlapIsReportedByConstraintName() {
		schedulePeriods.save(new DoctorSchedulePeriod(doctor.getId(), DayOfWeek.MONDAY, LocalTime.of(9, 0),
				LocalTime.of(12, 0)));
		scheduleExceptions.save(ScheduleException.extraAvailability(doctor.getClinicId(), doctor.getId(),
				LocalDate.of(2027, 4, 17), LocalTime.of(9, 0), LocalTime.of(11, 0), null, owner.getId()));
		flushAndClear();

		assertThat(schedulePeriods.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctor.getId()))
			.singleElement()
			.satisfies(period -> assertThat(period.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY));
		assertThat(scheduleExceptions.findAllByDoctorIdAndExceptionDateBetweenOrderByExceptionDateAscStartTimeAsc(
				doctor.getId(), LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 30)))
			.singleElement()
			.extracting(ScheduleException::getType).isEqualTo(ScheduleExceptionType.EXTRA_AVAILABILITY);

		schedulePeriods.save(new DoctorSchedulePeriod(doctor.getId(), DayOfWeek.MONDAY, LocalTime.of(11, 0),
				LocalTime.of(13, 0)));
		assertThatThrownBy(() -> schedulePeriods.flush())
			.isInstanceOf(DataIntegrityViolationException.class)
			.satisfies(ex -> assertThat(DatabaseConstraintCodes.constraintName(ex))
				.contains("doctor_schedule_period_no_overlap"));
	}

	@Test
	void appointmentConflictQueryIgnoresCancelledAppointments() {
		Appointment booked = appointments.save(appointment(NINE, 30));
		Appointment cancelled = appointment(NINE.plus(Duration.ofHours(1)), 30);
		cancelled.cancel("Patient request", NINE, owner.getId());
		appointments.save(cancelled);
		flushAndClear();

		assertThat(appointments.existsConflict(doctor.getId(), NINE.plusSeconds(600), NINE.plusSeconds(1200), null))
			.isTrue();
		assertThat(appointments.existsConflict(doctor.getId(), NINE.plusSeconds(600), NINE.plusSeconds(1200),
				booked.getId()))
			.isFalse();
		assertThat(appointments.existsConflict(doctor.getId(), NINE.plus(Duration.ofHours(1)),
				NINE.plus(Duration.ofMinutes(90)), null))
			.isFalse();
		assertThat(appointments.findOverlapping(clinic.getId(), List.of(doctor.getId()), NINE,
				NINE.plus(Duration.ofHours(2))))
			.extracting(Appointment::getStatus)
			.containsExactly(AppointmentStatus.SCHEDULED, AppointmentStatus.CANCELLED);
	}

	@Test
	void doubleBookingIsReportedByConstraintName() {
		appointments.saveAndFlush(appointment(NINE, 30));
		appointments.save(appointment(NINE.plusSeconds(900), 30));

		assertThatThrownBy(() -> appointments.flush())
			.isInstanceOf(DataIntegrityViolationException.class)
			.satisfies(ex -> assertThat(DatabaseConstraintCodes.constraintName(ex)).contains("appointment_no_overlap"));
	}

	@Test
	void consultationAndDocumentRoundTrip() throws Exception {
		Appointment appointment = appointments.save(appointment(NINE, 30));
		Consultation consultation = consultations.save(new Consultation(clinic.getId(), record.getId(), doctor.getId(),
				appointment.getId(), NINE, owner.getId()));
		String checksum = sha256("file-bytes");
		documents.save(new MedicalDocument(clinic.getId(), record.getId(), consultation.getId(), "report.pdf",
				"application/pdf", 1024, checksum, "clinics/test/" + checksum, owner.getId()));
		flushAndClear();

		assertThat(consultations.findByAppointmentId(appointment.getId())).isPresent();
		assertThat(documents.search(record.getId(), null, null, PageRequest.of(0, 20)))
			.singleElement()
			.satisfies(document -> {
				assertThat(document.getChecksumSha256()).isEqualTo(checksum);
				assertThat(document.getCreatedAt()).isNotNull();
			});
	}

	@Test
	void auditEventMetadataRoundTripsAsJson() {
		AuditEvent event = auditEvents.save(new AuditEvent(clinic.getId(), owner.getId(), "PATIENT_CREATED", "PATIENT",
				patient.getId(), NINE, Map.of("source", "test", "count", 2)));
		flushAndClear();

		AuditEvent loaded = auditEvents.findAllByClinicId(clinic.getId(), PageRequest.of(0, 10)).getContent().getFirst();
		assertThat(loaded.getId()).isEqualTo(event.getId());
		assertThat(loaded.getMetadata()).containsEntry("source", "test").containsEntry("count", 2);
	}

	@Test
	void userTokensAreFoundByHashAndCanBeInvalidated() throws Exception {
		byte[] hash = MessageDigest.getInstance("SHA-256").digest("raw-token".getBytes(StandardCharsets.UTF_8));
		userTokens.save(new UserToken(owner.getId(), TokenPurpose.INVITATION, hash,
				Instant.now().plus(72, ChronoUnit.HOURS)));
		flushAndClear();

		UserToken token = userTokens.findByTokenHashAndPurpose(hash, TokenPurpose.INVITATION).orElseThrow();
		assertThat(token.isUsable(Instant.now())).isTrue();

		assertThat(userTokens.findAllByUserIdAndPurposeAndUsedAtIsNull(owner.getId(), TokenPurpose.INVITATION))
			.singleElement()
			.satisfies(open -> open.markUsed(Instant.now()));
		flushAndClear();
		assertThat(userTokens.findById(token.getId()).orElseThrow().isUsable(Instant.now())).isFalse();
	}

	private Appointment appointment(Instant start, int minutes) {
		return new Appointment(clinic.getId(), patient.getId(), doctor.getId(), start,
				start.plus(Duration.ofMinutes(minutes)), AppointmentType.FOLLOW_UP, null, null, owner.getId());
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	private static String sha256(String value) throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(digest);
	}

}
