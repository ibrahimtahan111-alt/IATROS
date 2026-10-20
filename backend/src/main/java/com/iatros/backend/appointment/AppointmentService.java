package com.iatros.backend.appointment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.access.AccessPolicy;
import com.iatros.backend.access.DataScope;
import com.iatros.backend.appointment.dto.AppointmentResponse;
import com.iatros.backend.appointment.dto.AppointmentSummaryResponse;
import com.iatros.backend.appointment.dto.CancelAppointmentRequest;
import com.iatros.backend.appointment.dto.ChangeAppointmentStatusRequest;
import com.iatros.backend.appointment.dto.CreateAppointmentRequest;
import com.iatros.backend.appointment.dto.NameRef;
import com.iatros.backend.appointment.dto.RescheduleAppointmentRequest;
import com.iatros.backend.appointment.dto.UpdateAppointmentRequest;
import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.clinic.ClinicTime;
import com.iatros.backend.consultation.Consultation;
import com.iatros.backend.consultation.ConsultationRepository;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.patient.Patient;
import com.iatros.backend.patient.PatientRepository;
import com.iatros.backend.patient.PatientService;
import com.iatros.backend.patient.PatientStatus;
import com.iatros.backend.schedule.WorkingHoursService;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.BusinessRuleException;
import com.iatros.backend.shared.error.ConflictException;
import com.iatros.backend.shared.error.ForbiddenOperationException;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.persistence.Versions;
import com.iatros.backend.shared.text.Text;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.user.Role;

/**
 * Appointments (api.md §17–18, decisions.md D-024, D-025, D-027, D-038). Owners and secretaries work on every
 * clinic appointment, doctors on their own. Every booking is checked against the doctor's working hours and
 * existing appointments; the appointment_no_overlap constraint is the final guarantee under concurrency (D-005).
 */
@Service
public class AppointmentService {

	/** Tolerance for client clocks running slightly behind when booking "now". */
	static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

	/** Statuses that record the patient's arrival or absence: only from the appointment's day on (D-038). */
	private static final Set<AppointmentStatus> DAY_OF_STATUSES = EnumSet.of(AppointmentStatus.WAITING,
			AppointmentStatus.IN_CONSULTATION, AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW);

	/** Open ends of a date filter, within PostgreSQL's timestamptz range. */
	private static final Instant EARLIEST = Instant.parse("0001-01-01T00:00:00Z");
	private static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");

	private final AppointmentRepository appointments;
	private final AppointmentDirectory directory;
	private final PatientRepository patients;
	private final DoctorRepository doctors;
	private final ConsultationRepository consultations;
	private final WorkingHoursService workingHours;
	private final AccessPolicy access;
	private final ClinicTime clinicTime;
	private final AuditService audit;
	private final Clock clock;

	AppointmentService(AppointmentRepository appointments, AppointmentDirectory directory, PatientRepository patients,
			DoctorRepository doctors, ConsultationRepository consultations, WorkingHoursService workingHours,
			AccessPolicy access, ClinicTime clinicTime, AuditService audit, Clock clock) {
		this.appointments = appointments;
		this.directory = directory;
		this.patients = patients;
		this.doctors = doctors;
		this.consultations = consultations;
		this.workingHours = workingHours;
		this.access = access;
		this.clinicTime = clinicTime;
		this.audit = audit;
		this.clock = clock;
	}

	/** {@code from}/{@code to} are clinic-local dates of the start, both inclusive. Empty filters mean "all". */
	@Transactional(readOnly = true)
	public PageResponse<AppointmentSummaryResponse> list(AuthenticatedUser caller, UUID doctorId, UUID patientId,
			Collection<AppointmentStatus> statuses, Collection<AppointmentType> types, LocalDate from, LocalDate to,
			Pageable pageable) {
		if (from != null && to != null && to.isBefore(from)) {
			throw ValidationException.of("to", "END_BEFORE_START");
		}
		DataScope scope = access.dataScope(caller);
		ZoneId zone = clinicTime.zone(caller.clinicId());
		Page<Appointment> page = appointments.search(caller.clinicId(), scope.doctorId().orElse(null), doctorId,
				patientId, orAll(statuses, AppointmentStatus.values()), orAll(types, AppointmentType.values()),
				from == null ? EARLIEST : from.atStartOfDay(zone).toInstant(),
				to == null ? LATEST : to.plusDays(1).atStartOfDay(zone).toInstant(), pageable);
		Map<UUID, NameRef> patientRefs = directory
			.patients(page.getContent().stream().map(Appointment::getPatientId).collect(Collectors.toSet()));
		Map<UUID, NameRef> doctorRefs = directory
			.doctors(page.getContent().stream().map(Appointment::getDoctorId).collect(Collectors.toSet()));
		return PageResponse.from(page, appointment -> new AppointmentSummaryResponse(appointment.getId(),
				appointment.getStartAt(), appointment.getEndAt(), patientRefs.get(appointment.getPatientId()),
				doctorRefs.get(appointment.getDoctorId()), appointment.getType(), appointment.getStatus(),
				appointment.getReason()));
	}

	@Transactional(readOnly = true)
	public AppointmentResponse get(AuthenticatedUser caller, UUID appointmentId) {
		return toResponse(caller, access.requireAppointment(caller, appointmentId));
	}

	@Transactional
	public AppointmentResponse create(AuthenticatedUser caller, CreateAppointmentRequest request) {
		Patient patient = access.requireCanBook(caller, request.doctorId(), request.patientId());
		PatientService.requireActive(patient);
		Doctor doctor = doctors.findById(request.doctorId()).orElseThrow();
		requireBookable(doctor, request.startAt(), request.endAt(), null);

		Appointment appointment = new Appointment(caller.clinicId(), patient.getId(), doctor.getId(),
				request.startAt(), request.endAt(), request.type(), Text.trimToNull(request.reason()),
				Text.trimToNull(request.notes()), caller.userId());
		appointments.saveAndFlush(appointment);
		audit.record(caller.clinicId(), caller.userId(), AuditActions.APPOINTMENT_CREATED,
				AuditActions.ENTITY_APPOINTMENT, appointment.getId(), references(appointment));
		return toResponse(caller, appointment);
	}

	@Transactional
	public AppointmentResponse update(AuthenticatedUser caller, UUID appointmentId, UpdateAppointmentRequest request) {
		Appointment appointment = access.requireAppointment(caller, appointmentId);
		Versions.require(appointment, request.version());
		PatientService.requireActive(patientOf(appointment));
		List<String> changedFields = new ArrayList<>();
		addIfChanged(changedFields, "type", appointment.getType(), request.type());
		addIfChanged(changedFields, "reason", appointment.getReason(), Text.trimToNull(request.reason()));
		addIfChanged(changedFields, "notes", appointment.getNotes(), Text.trimToNull(request.notes()));
		appointment.updateDetails(request.type(), Text.trimToNull(request.reason()), Text.trimToNull(request.notes()),
				caller.userId());
		appointments.flush();
		Map<String, Object> metadata = new HashMap<>(references(appointment));
		metadata.put("changedFields", changedFields);
		audit.record(caller.clinicId(), caller.userId(), AuditActions.APPOINTMENT_UPDATED,
				AuditActions.ENTITY_APPOINTMENT, appointment.getId(), metadata);
		return toResponse(caller, appointment);
	}

	/** Moves the appointment to another time and, for owners and secretaries, possibly another doctor. */
	@Transactional
	public AppointmentResponse reschedule(AuthenticatedUser caller, UUID appointmentId,
			RescheduleAppointmentRequest request) {
		Appointment appointment = access.requireAppointment(caller, appointmentId);
		Versions.require(appointment, request.version());
		Patient patient = patientOf(appointment);
		PatientService.requireActive(patient);
		if (!appointment.getStatus().isEditable()) {
			throw notEditable(appointment);
		}
		if (!request.doctorId().equals(appointment.getDoctorId())) {
			access.requireCanBook(caller, request.doctorId(), patient.getId());
		}
		Doctor doctor = doctors.findById(request.doctorId()).orElseThrow();
		requireBookable(doctor, request.startAt(), request.endAt(), appointment.getId());

		appointment.reschedule(doctor.getId(), request.startAt(), request.endAt(), caller.userId());
		appointments.flush();
		audit.record(caller.clinicId(), caller.userId(), AuditActions.APPOINTMENT_RESCHEDULED,
				AuditActions.ENTITY_APPOINTMENT, appointment.getId(), references(appointment));
		return toResponse(caller, appointment);
	}

	/** Status changes along D-024, except cancellation, which has its own operation. */
	@Transactional
	public AppointmentResponse changeStatus(AuthenticatedUser caller, UUID appointmentId,
			ChangeAppointmentStatusRequest request) {
		Appointment appointment = access.requireAppointment(caller, appointmentId);
		Versions.require(appointment, request.version());
		AppointmentStatus next = request.status();
		if (next.requiresClinicalActor() && !access.isClinicalActor(caller, appointment)) {
			throw new ForbiddenOperationException("FORBIDDEN",
					"Only the owner or the appointment's doctor can start or complete it");
		}
		if (next == AppointmentStatus.CANCELLED || !appointment.getStatus().canTransitionTo(next)) {
			throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
					"Cannot change appointment status from " + appointment.getStatus() + " to " + next);
		}
		if (DAY_OF_STATUSES.contains(next) && !hasStarted(appointment)) {
			throw new BusinessRuleException("APPOINTMENT_NOT_STARTED",
					"This status can only be set from the appointment's day on");
		}
		AppointmentStatus previous = appointment.getStatus();
		appointment.changeStatus(next, Instant.now(clock), caller.userId());
		appointments.flush();
		Map<String, Object> metadata = new HashMap<>(references(appointment));
		metadata.put("from", previous.name());
		metadata.put("to", next.name());
		audit.record(caller.clinicId(), caller.userId(), AuditActions.APPOINTMENT_STATUS_CHANGED,
				AuditActions.ENTITY_APPOINTMENT, appointment.getId(), metadata);
		return toResponse(caller, appointment);
	}

	/** Cancels and keeps the appointment, which stays visible in history (D-030); its time becomes free. */
	@Transactional
	public AppointmentResponse cancel(AuthenticatedUser caller, UUID appointmentId, CancelAppointmentRequest request) {
		Appointment appointment = access.requireAppointment(caller, appointmentId);
		Versions.require(appointment, request.version());
		appointment.cancel(Text.trimToNull(request.reason()), Instant.now(clock), caller.userId());
		appointments.flush();
		audit.record(caller.clinicId(), caller.userId(), AuditActions.APPOINTMENT_CANCELLED,
				AuditActions.ENTITY_APPOINTMENT, appointment.getId(), references(appointment));
		return toResponse(caller, appointment);
	}

	/**
	 * Booking rules shared by create and reschedule: a valid range, not in the past, inside one working range of the
	 * doctor (D-025), and free of the doctor's other time-blocking appointments.
	 */
	private void requireBookable(Doctor doctor, Instant startAt, Instant endAt, UUID excludeId) {
		Appointment.requireValidTimes(startAt, endAt);
		if (startAt.isBefore(Instant.now(clock).minus(CLOCK_SKEW))) {
			throw ValidationException.of("startAt", "DATE_IN_PAST");
		}
		if (!workingHours.isAvailable(doctor, startAt, endAt)) {
			throw new BusinessRuleException("DOCTOR_UNAVAILABLE", "The doctor does not work at this time");
		}
		if (appointments.existsConflict(doctor.getId(), startAt, endAt, excludeId)) {
			throw new ConflictException("APPOINTMENT_CONFLICT", "The doctor already has an appointment at that time");
		}
	}

	private boolean hasStarted(Appointment appointment) {
		ZoneId zone = clinicTime.zone(appointment.getClinicId());
		return !LocalDate.ofInstant(appointment.getStartAt(), zone).isAfter(clinicTime.today(appointment.getClinicId()));
	}

	private Patient patientOf(Appointment appointment) {
		return patients.findByIdAndClinicId(appointment.getPatientId(), appointment.getClinicId()).orElseThrow();
	}

	private AppointmentResponse toResponse(AuthenticatedUser caller, Appointment appointment) {
		Patient patient = patientOf(appointment);
		NameRef doctor = directory.doctors(List.of(appointment.getDoctorId())).get(appointment.getDoctorId());
		boolean clinicalActor = access.isClinicalActor(caller, appointment);
		boolean started = hasStarted(appointment);
		List<AppointmentStatus> nextStatuses = appointment.getStatus()
			.allowedNextStatuses()
			.stream()
			.filter(next -> next != AppointmentStatus.CANCELLED)
			.filter(next -> clinicalActor || !next.requiresClinicalActor())
			.filter(next -> started || !DAY_OF_STATUSES.contains(next))
			.sorted()
			.toList();
		UUID consultationId = caller.hasRole(Role.SECRETARY) ? null
				: consultations.findByAppointmentId(appointment.getId()).map(Consultation::getId).orElse(null);
		return new AppointmentResponse(appointment.getId(),
				new NameRef(patient.getId(), patient.getFirstName(), patient.getLastName()), doctor,
				appointment.getStartAt(), appointment.getEndAt(), appointment.getType(), appointment.getStatus(),
				appointment.getReason(), appointment.getNotes(), appointment.getCancellationReason(),
				appointment.getStatusChangedAt(), directory.user(appointment.getCreatedBy()),
				appointment.getCreatedAt(), appointment.getUpdatedAt(),
				appointment.getStatus().isEditable() && patient.getStatus() == PatientStatus.ACTIVE,
				appointment.getStatus().canTransitionTo(AppointmentStatus.CANCELLED), nextStatuses, consultationId,
				appointment.getVersion());
	}

	private static BusinessRuleException notEditable(Appointment appointment) {
		return new BusinessRuleException("APPOINTMENT_NOT_EDITABLE",
				"Appointment in status " + appointment.getStatus() + " cannot be edited");
	}

	/** Identifiers only: reasons and notes never enter the audit trail (security.md). */
	private static Map<String, Object> references(Appointment appointment) {
		return Map.of("patientId", appointment.getPatientId().toString(), "doctorId",
				appointment.getDoctorId().toString());
	}

	private static void addIfChanged(List<String> changed, String field, Object current, Object requested) {
		if (!Objects.equals(current, requested)) {
			changed.add(field);
		}
	}

	@SafeVarargs
	private static <T> Collection<T> orAll(Collection<T> selected, T... all) {
		return selected == null || selected.isEmpty() ? Arrays.asList(all) : selected;
	}

}
