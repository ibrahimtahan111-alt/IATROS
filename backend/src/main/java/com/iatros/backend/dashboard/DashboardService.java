package com.iatros.backend.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.access.AccessPolicy;
import com.iatros.backend.activity.ActivityService;
import com.iatros.backend.appointment.Appointment;
import com.iatros.backend.appointment.AppointmentDirectory;
import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.PatientAppointmentTime;
import com.iatros.backend.appointment.dto.NameRef;
import com.iatros.backend.clinic.ClinicTime;
import com.iatros.backend.dashboard.dto.DaySummary;
import com.iatros.backend.dashboard.dto.DoctorDashboardResponse;
import com.iatros.backend.dashboard.dto.DoctorDashboardResponse.RecentPatient;
import com.iatros.backend.dashboard.dto.OwnerDashboardResponse;
import com.iatros.backend.dashboard.dto.OwnerDashboardResponse.DoctorDay;
import com.iatros.backend.dashboard.dto.ScheduleItem;
import com.iatros.backend.dashboard.dto.ScheduleItem.ClinicalAlerts;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.patient.PatientDoctor;
import com.iatros.backend.patient.PatientDoctorRepository;
import com.iatros.backend.patient.PatientRepository;
import com.iatros.backend.patient.PatientStatus;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ForbiddenOperationException;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;
import com.iatros.backend.user.UserStatus;

/**
 * Role dashboards (api.md §22, decisions.md D-039): one request per screen, aggregated from the authoritative
 * records. The owner dashboard covers the clinic; the doctor dashboard only the caller's own appointments and
 * authorized patients. Secretaries have no dashboard; their home is the calendar.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

	static final int MAX_SCHEDULE_ITEMS = 100;
	static final int UPCOMING_DAYS = 7;
	static final int RECENT_PATIENTS = 5;
	static final int RECENT_ACTIVITY = 8;

	private final AppointmentRepository appointments;
	private final AppointmentDirectory directory;
	private final DoctorRepository doctors;
	private final PatientRepository patients;
	private final PatientDoctorRepository patientDoctors;
	private final ClinicalAlertQueries alerts;
	private final ActivityService activity;
	private final AccessPolicy access;
	private final ClinicTime clinicTime;

	DashboardService(AppointmentRepository appointments, AppointmentDirectory directory, DoctorRepository doctors,
			PatientRepository patients, PatientDoctorRepository patientDoctors, ClinicalAlertQueries alerts,
			ActivityService activity, AccessPolicy access, ClinicTime clinicTime) {
		this.appointments = appointments;
		this.directory = directory;
		this.doctors = doctors;
		this.patients = patients;
		this.patientDoctors = patientDoctors;
		this.alerts = alerts;
		this.activity = activity;
		this.access = access;
		this.clinicTime = clinicTime;
	}

	public OwnerDashboardResponse owner(AuthenticatedUser caller) {
		if (!caller.hasRole(Role.OWNER)) {
			throw forbidden();
		}
		UUID clinicId = caller.clinicId();
		ZoneId zone = clinicTime.zone(clinicId);
		LocalDate today = clinicTime.today(clinicId);
		List<Appointment> day = appointments.findStarting(clinicId, null, startOf(today, zone),
				startOf(today.plusDays(1), zone));
		List<Appointment> todays = active(day);

		List<Doctor> clinicDoctors = doctors.findAllByClinicId(clinicId);
		Map<UUID, AppUser> users = directory.usersById(clinicDoctors.stream().map(Doctor::getUserId).toList());
		List<Doctor> activeDoctors = clinicDoctors.stream()
			.filter(doctor -> users.get(doctor.getUserId()).getStatus() == UserStatus.ACTIVE)
			.toList();
		Map<UUID, List<Appointment>> byDoctor = todays.stream()
			.collect(Collectors.groupingBy(Appointment::getDoctorId));
		List<DoctorDay> doctorDays = clinicDoctors.stream()
			.filter(doctor -> activeDoctors.contains(doctor) || byDoctor.containsKey(doctor.getId()))
			.map(doctor -> doctorDay(doctor, users.get(doctor.getUserId()), byDoctor.getOrDefault(doctor.getId(),
					List.of())))
			.sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.doctor().lastName() + a.doctor().firstName(),
					b.doctor().lastName() + b.doctor().firstName()))
			.toList();

		List<Appointment> shown = todays.stream().limit(MAX_SCHEDULE_ITEMS).toList();
		return new OwnerDashboardResponse(zone.getId(), today, summary(todays, cancelledOf(day).size()),
				patients.countByClinicIdAndStatus(clinicId, PatientStatus.ACTIVE), activeDoctors.size(), doctorDays,
				schedule(shown, Map.of(), Map.of(), Set.of()), todays.size() > shown.size(),
				activity.recent(caller, null, RECENT_ACTIVITY));
	}

	public DoctorDashboardResponse doctor(AuthenticatedUser caller) {
		if (caller.hasRole(Role.SECRETARY)) {
			throw forbidden();
		}
		UUID doctorId = access.ownDoctorId(caller).orElseThrow(DashboardService::forbidden);
		Doctor doctor = access.requireDoctorManagement(caller, doctorId);
		UUID clinicId = caller.clinicId();
		ZoneId zone = clinicTime.zone(clinicId);
		LocalDate today = clinicTime.today(clinicId);
		List<Appointment> day = appointments.findStarting(clinicId, doctorId, startOf(today, zone),
				startOf(today.plusDays(1), zone));
		List<Appointment> todays = active(day);
		long upcoming = appointments
			.findStarting(clinicId, doctorId, startOf(today.plusDays(1), zone),
					startOf(today.plusDays(1 + UPCOMING_DAYS), zone))
			.stream()
			.filter(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED
					|| appointment.getStatus() == AppointmentStatus.CONFIRMED)
			.count();

		Set<UUID> authorized = authorizedPatients(doctorId,
				todays.stream().map(Appointment::getPatientId).collect(Collectors.toSet()));
		List<ScheduleItem> schedule = schedule(todays, alerts.severeAllergies(clinicId, authorized),
				alerts.activeConditions(clinicId, authorized), authorized);

		List<PatientAppointmentTime> seen = appointments.findRecentlySeen(doctorId,
				PageRequest.of(0, RECENT_PATIENTS));
		Map<UUID, NameRef> seenRefs = directory
			.patients(seen.stream().map(PatientAppointmentTime::patientId).toList());
		List<RecentPatient> recentPatients = seen.stream()
			.map(visit -> new RecentPatient(seenRefs.get(visit.patientId()), visit.startAt()))
			.toList();

		return new DoctorDashboardResponse(zone.getId(), today,
				directory.doctorRefs(List.of(doctor)).get(doctorId), summary(todays, cancelledOf(day).size()),
				upcoming, patientDoctors.countActivePatients(doctorId),
				patientDoctors.countActivePrimaryPatients(doctorId), schedule, recentPatients,
				activity.recent(caller, doctorId, RECENT_ACTIVITY));
	}

	private Set<UUID> authorizedPatients(UUID doctorId, Collection<UUID> patientIds) {
		if (patientIds.isEmpty()) {
			return Set.of();
		}
		return patientDoctors.findAllByDoctorIdAndPatientIdIn(doctorId, patientIds)
			.stream()
			.map(PatientDoctor::getPatientId)
			.collect(Collectors.toSet());
	}

	/** Alerts are attached only to patients in {@code withAlerts}. */
	private List<ScheduleItem> schedule(List<Appointment> shown, Map<UUID, List<String>> allergies,
			Map<UUID, List<String>> conditions, Set<UUID> withAlerts) {
		Map<UUID, NameRef> patientRefs = directory
			.patients(shown.stream().map(Appointment::getPatientId).collect(Collectors.toSet()));
		Map<UUID, NameRef> doctorRefs = directory
			.doctors(shown.stream().map(Appointment::getDoctorId).collect(Collectors.toSet()));
		return shown.stream()
			.map(appointment -> new ScheduleItem(appointment.getId(), appointment.getStartAt(),
					appointment.getEndAt(), patientRefs.get(appointment.getPatientId()),
					doctorRefs.get(appointment.getDoctorId()), appointment.getType(), appointment.getStatus(),
					withAlerts.contains(appointment.getPatientId())
							? new ClinicalAlerts(allergies.getOrDefault(appointment.getPatientId(), List.of()),
									conditions.getOrDefault(appointment.getPatientId(), List.of()))
							: null))
			.toList();
	}

	private static DaySummary summary(List<Appointment> todays, long cancelled) {
		return new DaySummary(todays.size(), todays.stream().map(Appointment::getPatientId).distinct().count(),
				count(todays, AppointmentStatus.SCHEDULED) + count(todays, AppointmentStatus.CONFIRMED),
				count(todays, AppointmentStatus.WAITING), count(todays, AppointmentStatus.IN_CONSULTATION),
				count(todays, AppointmentStatus.COMPLETED), count(todays, AppointmentStatus.NO_SHOW), cancelled);
	}

	private static DoctorDay doctorDay(Doctor doctor, AppUser user, List<Appointment> todays) {
		return new DoctorDay(new NameRef(doctor.getId(), user.getFirstName(), user.getLastName()), todays.size(),
				count(todays, AppointmentStatus.COMPLETED),
				count(todays, AppointmentStatus.WAITING) + count(todays, AppointmentStatus.IN_CONSULTATION),
				count(todays, AppointmentStatus.SCHEDULED) + count(todays, AppointmentStatus.CONFIRMED),
				count(todays, AppointmentStatus.NO_SHOW));
	}

	private static List<Appointment> active(List<Appointment> found) {
		return found.stream().filter(appointment -> appointment.getStatus() != AppointmentStatus.CANCELLED).toList();
	}

	private static List<Appointment> cancelledOf(List<Appointment> found) {
		return found.stream().filter(appointment -> appointment.getStatus() == AppointmentStatus.CANCELLED).toList();
	}

	private static long count(List<Appointment> found, AppointmentStatus status) {
		return found.stream().filter(appointment -> appointment.getStatus() == status).count();
	}

	private static Instant startOf(LocalDate date, ZoneId zone) {
		return date.atStartOfDay(zone).toInstant();
	}

	private static ForbiddenOperationException forbidden() {
		return new ForbiddenOperationException("FORBIDDEN", "Operation not permitted for this role");
	}

}
