package com.iatros.backend.dev;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.iatros.backend.appointment.Appointment;
import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.clinic.ClinicTime;
import com.iatros.backend.consultation.Consultation;
import com.iatros.backend.consultation.ConsultationRepository;
import com.iatros.backend.dev.DevDayPlanner.Booking;
import com.iatros.backend.dev.DevDayPlanner.Note;
import com.iatros.backend.dev.DevDayPlanner.PriorVisit;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.medicalrecord.MedicalRecordRepository;
import com.iatros.backend.patient.Patient;
import com.iatros.backend.patient.PatientRepository;
import com.iatros.backend.patient.PatientStatus;
import com.iatros.backend.schedule.ScheduleException;
import com.iatros.backend.schedule.ScheduleExceptionRepository;
import com.iatros.backend.schedule.ScheduleExceptionType;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;

/**
 * Continues the fictional demo clinic one day at a time (decisions.md D-017). Runs only in the {@code dev} profile.
 * A day that already has appointments is left as it is, so the start-up seed and this job never double-book it.
 */
@Component
@Profile("dev")
class DevDailyClinic {

	private static final Logger log = LoggerFactory.getLogger(DevDailyClinic.class);

	private static final Duration VISIT = Duration.ofMinutes(30);

	/** A slot that started at least this long ago is treated as finished. */
	private static final Duration FINISHED_AFTER = Duration.ofMinutes(40);

	private static final LocalTime[] WEEKDAY = { LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0),
			LocalTime.of(16, 0) };

	private static final LocalTime[] WEEKEND = { LocalTime.of(9, 0), LocalTime.of(10, 30), LocalTime.of(12, 0) };

	private final Clock clock;

	private final ClinicTime clinicTime;

	private final TransactionTemplate transactions;

	private final AppUserRepository users;

	private final DoctorRepository doctors;

	private final PatientRepository patients;

	private final AppointmentRepository appointments;

	private final ConsultationRepository consultations;

	private final MedicalRecordRepository records;

	private final ScheduleExceptionRepository exceptions;

	DevDailyClinic(Clock clock, ClinicTime clinicTime, PlatformTransactionManager transactions, AppUserRepository users,
			DoctorRepository doctors, PatientRepository patients, AppointmentRepository appointments,
			ConsultationRepository consultations, MedicalRecordRepository records, ScheduleExceptionRepository exceptions) {
		this.clock = clock;
		this.clinicTime = clinicTime;
		this.transactions = new TransactionTemplate(transactions);
		this.users = users;
		this.doctors = doctors;
		this.patients = patients;
		this.appointments = appointments;
		this.consultations = consultations;
		this.records = records;
		this.exceptions = exceptions;
	}

	/** Midnight in the seeded demo clinic zone. Start-up fills a day the process slept through. */
	@Scheduled(cron = "0 5 0 * * *", zone = "Europe/Athens")
	public void fillToday() {
		synchronized (this) {
			AppUser owner = users.findByEmail(DevDataSeeder.OWNER_EMAIL).orElse(null);
			if (owner == null) {
				return;
			}
			ZoneId zone;
			try {
				zone = clinicTime.zone(owner.getClinicId());
			}
			catch (IllegalStateException ex) {
				log.warn("Demo day skipped: {}", ex.getMessage());
				return;
			}
			LocalDate today = LocalDate.now(clock.withZone(zone));
			transactions.executeWithoutResult(status -> writeToday(owner, zone, today));
		}
	}

	private void writeToday(AppUser owner, ZoneId zone, LocalDate today) {
		UUID clinicId = owner.getClinicId();
		Instant from = today.atStartOfDay(zone).toInstant();
		Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
		List<Doctor> clinicDoctors = doctors.findAllByClinicId(clinicId);
		for (Doctor doctor : clinicDoctors) {
			if (!appointments.findStarting(clinicId, doctor.getId(), from, to).isEmpty()) {
				log.info("Demo day {} already has appointments; leaving it unchanged", today);
				return;
			}
		}
		Instant now = Instant.now(clock);
		Instant historyFrom = today.minusDays(120).atStartOfDay(zone).toInstant();
		int created = 0;
		for (Doctor doctor : clinicDoctors) {
			created += fillDoctor(owner, zone, today, now, historyFrom, from, doctor);
		}
		if (created > 0) {
			log.info("Demo day {} filled with {} fictional appointments", today, created);
		}
	}

	private int fillDoctor(AppUser owner, ZoneId zone, LocalDate today, Instant now, Instant historyFrom, Instant todayStart,
			Doctor doctor) {
		List<ScheduleException> dayExceptions = exceptions
			.findAllByDoctorIdAndExceptionDateBetweenOrderByExceptionDateAscStartTimeAsc(doctor.getId(), today, today);
		boolean off = dayExceptions.stream().anyMatch(exception -> exception.getType() == ScheduleExceptionType.UNAVAILABLE);
		if (off) {
			return 0;
		}
		List<Patient> panel = new ArrayList<>(patients
			.search(owner.getClinicId(), PatientStatus.ACTIVE, null, doctor.getId(), null, PageRequest.of(0, 100))
			.getContent());
		panel.sort(Comparator.comparing(Patient::getLastName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(Patient::getFirstName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(Patient::getId));
		if (panel.isEmpty()) {
			return 0;
		}
		boolean weekend = today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY;
		boolean alreadyOpen = dayExceptions.stream()
			.anyMatch(exception -> exception.getType() == ScheduleExceptionType.EXTRA_AVAILABILITY);
		if (weekend && !alreadyOpen) {
			exceptions.save(ScheduleException.extraAvailability(owner.getClinicId(), doctor.getId(), today, LocalTime.of(9, 0),
					LocalTime.of(13, 0), "Weekend clinic", owner.getId()));
		}
		LocalTime[] slots = weekend ? WEEKEND : WEEKDAY;
		List<PriorVisit> history = appointments.findStarting(owner.getClinicId(), doctor.getId(), historyFrom, todayStart)
			.stream()
			.map(appointment -> new PriorVisit(appointment.getPatientId(), appointment.getType(), appointment.getStatus(),
					appointment.getStartAt().atZone(zone).toLocalDate(), appointment.getStartAt()))
			.toList();
		List<Booking> bookings = DevDayPlanner.book(panel.stream().map(Patient::getId).toList(), history, today, slots.length);
		return place(owner, doctor, zone, today, now, slots, bookings);
	}

	private int place(AppUser owner, Doctor doctor, ZoneId zone, LocalDate today, Instant now, LocalTime[] slots,
			List<Booking> bookings) {
		int count = Math.min(bookings.size(), slots.length);
		List<Integer> future = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Instant start = today.atTime(slots[i]).atZone(zone).toInstant();
			if (start.isAfter(now)) {
				future.add(i);
			}
		}
		Integer cancelled = future.size() >= 2 ? future.get(future.size() - 1) : null;
		boolean missed = false;
		boolean inRoom = false;
		int saved = 0;
		for (int i = 0; i < count; i++) {
			Booking booking = bookings.get(i);
			Instant start = today.atTime(slots[i]).atZone(zone).toInstant();
			Instant end = start.plus(VISIT);
			Appointment appointment = new Appointment(owner.getClinicId(), booking.patientId(), doctor.getId(), start, end,
					booking.type(), booking.reason(), null, owner.getId());
			if (appointments.existsConflict(doctor.getId(), start, end, appointment.getId())) {
				continue;
			}
			boolean finished = false;
			if (cancelled != null && cancelled == i) {
				appointment.cancel("Patient asked to reschedule", now, owner.getId());
			}
			else if (start.isAfter(now)) {
				if (future.indexOf(i) % 2 == 0) {
					appointment.changeStatus(AppointmentStatus.CONFIRMED, now, owner.getId());
				}
			}
			else if (!now.isBefore(start.plus(FINISHED_AFTER))) {
				if (booking.likelyToMiss() && !missed) {
					appointment.changeStatus(AppointmentStatus.NO_SHOW, start.plus(VISIT), owner.getId());
					missed = true;
				}
				else {
					complete(appointment, start, owner.getId());
					finished = true;
				}
			}
			else if (!inRoom) {
				appointment.changeStatus(AppointmentStatus.WAITING, start, owner.getId());
				appointment.changeStatus(AppointmentStatus.IN_CONSULTATION, start.plus(Duration.ofMinutes(5)), owner.getId());
				inRoom = true;
			}
			else {
				appointment.changeStatus(AppointmentStatus.WAITING, start, owner.getId());
			}
			appointments.saveAndFlush(appointment);
			if (finished) {
				writeConsultation(owner, doctor, booking, appointment, today, start);
			}
			saved++;
		}
		return saved;
	}

	private static void complete(Appointment appointment, Instant start, UUID actorId) {
		appointment.changeStatus(AppointmentStatus.WAITING, start, actorId);
		appointment.changeStatus(AppointmentStatus.IN_CONSULTATION, start, actorId);
		appointment.changeStatus(AppointmentStatus.COMPLETED, start.plus(Duration.ofMinutes(25)), actorId);
	}

	private void writeConsultation(AppUser owner, Doctor doctor, Booking booking, Appointment appointment, LocalDate today,
			Instant start) {
		records.findByPatientIdAndClinicId(booking.patientId(), owner.getClinicId()).ifPresent(record -> {
			Note note = DevDayPlanner.note(booking, today);
			Consultation consultation = new Consultation(owner.getClinicId(), record.getId(), doctor.getId(),
					appointment.getId(), start.plus(Duration.ofMinutes(5)), owner.getId());
			consultation.updateClinicalContent(start.plus(Duration.ofMinutes(5)), booking.reason(), note.diagnosis(),
					note.clinicalNotes(), note.treatmentNotes(), note.followUpNotes(), note.followUpOn(), owner.getId());
			consultations.save(consultation);
		});
	}

}
