package com.iatros.backend.consultation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.access.AccessPolicy;
import com.iatros.backend.appointment.Appointment;
import com.iatros.backend.appointment.AppointmentRepository;
import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.audit.ChangedFields;
import com.iatros.backend.clinic.ClinicTime;
import com.iatros.backend.consultation.dto.ConsultationDoctorRef;
import com.iatros.backend.consultation.dto.ConsultationRequest;
import com.iatros.backend.consultation.dto.ConsultationResponse;
import com.iatros.backend.consultation.dto.ConsultationSummaryResponse;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.medicalrecord.MedicalRecord;
import com.iatros.backend.medicalrecord.MedicalRecordAccess;
import com.iatros.backend.patient.Patient;
import com.iatros.backend.patient.PatientService;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ConflictException;
import com.iatros.backend.shared.error.ForbiddenOperationException;
import com.iatros.backend.shared.error.NotFoundException;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.persistence.Versions;
import com.iatros.backend.shared.text.Text;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;

/**
 * Consultations (api.md §16, decisions.md D-037). Everyone with clinical access to the patient reads them; a doctor
 * records consultations as themselves and only the consultation's doctor edits it. Consultations are never deleted.
 */
@Service
public class ConsultationService {

	/** Tolerance for client clocks running slightly ahead when "now" is submitted. */
	private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

	/** Open ends of a date filter, within PostgreSQL's timestamptz range. */
	private static final Instant EARLIEST = Instant.parse("0001-01-01T00:00:00Z");
	private static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");

	private final ConsultationRepository consultations;
	private final AppointmentRepository appointments;
	private final MedicalRecordAccess recordAccess;
	private final AccessPolicy access;
	private final DoctorRepository doctors;
	private final AppUserRepository users;
	private final ClinicTime clinicTime;
	private final AuditService audit;
	private final Clock clock;

	ConsultationService(ConsultationRepository consultations, AppointmentRepository appointments,
			MedicalRecordAccess recordAccess, AccessPolicy access, DoctorRepository doctors, AppUserRepository users,
			ClinicTime clinicTime, AuditService audit, Clock clock) {
		this.consultations = consultations;
		this.appointments = appointments;
		this.recordAccess = recordAccess;
		this.access = access;
		this.doctors = doctors;
		this.users = users;
		this.clinicTime = clinicTime;
		this.audit = audit;
		this.clock = clock;
	}

	/** Newest first; {@code from}/{@code to} are clinic-local dates, both inclusive. */
	@Transactional(readOnly = true)
	public PageResponse<ConsultationSummaryResponse> list(AuthenticatedUser caller, UUID patientId, UUID doctorId,
			LocalDate from, LocalDate to, Pageable pageable) {
		MedicalRecord record = recordAccess.readable(caller, patientId);
		if (from != null && to != null && to.isBefore(from)) {
			throw ValidationException.of("to", "END_BEFORE_START");
		}
		ZoneId zone = clinicTime.zone(caller.clinicId());
		Instant start = from == null ? EARLIEST : from.atStartOfDay(zone).toInstant();
		Instant end = to == null ? LATEST : to.plusDays(1).atStartOfDay(zone).toInstant();
		Page<Consultation> found = consultations.search(record.getId(), doctorId, start, end, pageable);
		Map<UUID, ConsultationDoctorRef> doctorRefs = doctorRefs(
				found.getContent().stream().map(Consultation::getDoctorId).collect(Collectors.toSet()));
		return PageResponse.from(found, consultation -> new ConsultationSummaryResponse(consultation.getId(),
				consultation.getConsultedAt(), doctorRefs.get(consultation.getDoctorId()), consultation.getReason(),
				consultation.getDiagnosis(), consultation.getFollowUpOn()));
	}

	@Transactional(readOnly = true)
	public ConsultationResponse get(AuthenticatedUser caller, UUID consultationId) {
		Consultation consultation = requireConsultation(caller, consultationId);
		return toResponse(caller, consultation, patientOf(caller, consultation).getId());
	}

	@Transactional
	public ConsultationResponse create(AuthenticatedUser caller, UUID patientId, ConsultationRequest request) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		UUID doctorId = access.ownDoctorId(caller)
			.orElseThrow(() -> new ForbiddenOperationException("FORBIDDEN", "Only doctors record consultations"));
		validate(caller, request);
		UUID appointmentId = request.appointmentId() == null ? null
				: requireLinkableAppointment(caller, request.appointmentId(), patientId, doctorId);
		Consultation consultation = new Consultation(caller.clinicId(), record.getId(), doctorId, appointmentId,
				request.consultedAt(), caller.userId());
		apply(consultation, request, caller);
		consultations.saveAndFlush(consultation);
		audit.record(caller.clinicId(), caller.userId(), AuditActions.CONSULTATION_CREATED,
				AuditActions.ENTITY_CONSULTATION, consultation.getId(), Map.of("patientId", patientId.toString()));
		return toResponse(caller, consultation, patientId);
	}

	@Transactional
	public ConsultationResponse update(AuthenticatedUser caller, UUID consultationId, ConsultationRequest request) {
		Consultation consultation = requireConsultation(caller, consultationId);
		Patient patient = patientOf(caller, consultation);
		PatientService.requireActive(patient);
		if (!isOwnConsultation(caller, consultation)) {
			throw new ForbiddenOperationException("FORBIDDEN", "Only the consultation's doctor can edit it");
		}
		if (request.version() == null) {
			throw ValidationException.of("version", "REQUIRED");
		}
		Versions.require(consultation, request.version());
		validate(caller, request);
		List<String> changedFields = changedFields(consultation, request);
		apply(consultation, request, caller);
		consultations.flush();
		audit.record(caller.clinicId(), caller.userId(), AuditActions.CONSULTATION_UPDATED,
				AuditActions.ENTITY_CONSULTATION, consultation.getId(),
				Map.of("patientId", patient.getId().toString(), "changedFields", changedFields));
		return toResponse(caller, consultation, patient.getId());
	}

	/**
	 * An appointment a new consultation may be linked to: the caller's own appointment with this patient that took
	 * or is taking place (not cancelled or a no-show), and not already linked (D-038).
	 */
	private UUID requireLinkableAppointment(AuthenticatedUser caller, UUID appointmentId, UUID patientId,
			UUID doctorId) {
		Appointment appointment = appointments.findByIdAndClinicId(appointmentId, caller.clinicId())
			.filter(found -> found.getPatientId().equals(patientId) && found.getDoctorId().equals(doctorId))
			.filter(found -> found.getStatus().blocksTime())
			.orElseThrow(() -> ValidationException.of("appointmentId", "INVALID"));
		if (consultations.findByAppointmentId(appointment.getId()).isPresent()) {
			throw new ConflictException("CONSULTATION_ALREADY_EXISTS", "The appointment already has a consultation");
		}
		return appointment.getId();
	}

	private Consultation requireConsultation(AuthenticatedUser caller, UUID consultationId) {
		return consultations.findByIdAndClinicId(consultationId, caller.clinicId())
			.orElseThrow(() -> NotFoundException.of("CONSULTATION"));
	}

	/** The consultation's patient, if the caller has clinical access to it; otherwise the consultation is hidden. */
	private Patient patientOf(AuthenticatedUser caller, Consultation consultation) {
		try {
			return recordAccess.patientOf(caller, consultation.getMedicalRecordId());
		}
		catch (NotFoundException hidden) {
			throw NotFoundException.of("CONSULTATION");
		}
	}

	private boolean isOwnConsultation(AuthenticatedUser caller, Consultation consultation) {
		return access.ownDoctorId(caller).map(consultation.getDoctorId()::equals).orElse(false);
	}

	private void validate(AuthenticatedUser caller, ConsultationRequest request) {
		if (request.consultedAt().isAfter(Instant.now(clock).plus(CLOCK_SKEW))) {
			throw ValidationException.of("consultedAt", "DATE_IN_FUTURE");
		}
		if (request.followUpOn() != null) {
			LocalDate consultationDay = LocalDate.ofInstant(request.consultedAt(), clinicTime.zone(caller.clinicId()));
			if (request.followUpOn().isBefore(consultationDay)) {
				throw ValidationException.of("followUpOn", "BEFORE_CONSULTATION");
			}
		}
	}

	private static void apply(Consultation consultation, ConsultationRequest request, AuthenticatedUser caller) {
		consultation.updateClinicalContent(request.consultedAt(), Text.trim(request.reason()),
				Text.trimToNull(request.diagnosis()), Text.trimToNull(request.clinicalNotes()),
				Text.trimToNull(request.treatmentNotes()), Text.trimToNull(request.followUpNotes()),
				request.followUpOn(), caller.userId());
	}

	/** Names of the fields an update changes, for the audit trail; never their content (security.md §23). */
	private static List<String> changedFields(Consultation current, ConsultationRequest request) {
		return new ChangedFields().compare("consultedAt", current.getConsultedAt(), request.consultedAt())
			.compare("reason", current.getReason(), Text.trim(request.reason()))
			.compare("diagnosis", current.getDiagnosis(), Text.trimToNull(request.diagnosis()))
			.compare("clinicalNotes", current.getClinicalNotes(), Text.trimToNull(request.clinicalNotes()))
			.compare("treatmentNotes", current.getTreatmentNotes(), Text.trimToNull(request.treatmentNotes()))
			.compare("followUpNotes", current.getFollowUpNotes(), Text.trimToNull(request.followUpNotes()))
			.compare("followUpOn", current.getFollowUpOn(), request.followUpOn())
			.names();
	}

	private ConsultationResponse toResponse(AuthenticatedUser caller, Consultation consultation, UUID patientId) {
		ConsultationDoctorRef doctor = doctorRefs(List.of(consultation.getDoctorId())).get(consultation.getDoctorId());
		return new ConsultationResponse(consultation.getId(), patientId, consultation.getConsultedAt(), doctor,
				consultation.getReason(), consultation.getDiagnosis(), consultation.getClinicalNotes(),
				consultation.getTreatmentNotes(), consultation.getFollowUpNotes(), consultation.getFollowUpOn(),
				consultation.getAppointmentId(), consultation.getCreatedAt(), consultation.getUpdatedAt(),
				isOwnConsultation(caller, consultation), consultation.getVersion());
	}

	private Map<UUID, ConsultationDoctorRef> doctorRefs(Collection<UUID> doctorIds) {
		if (doctorIds.isEmpty()) {
			return Map.of();
		}
		List<Doctor> found = doctors.findAllById(doctorIds);
		Map<UUID, AppUser> usersById = users.findAllById(found.stream().map(Doctor::getUserId).toList())
			.stream()
			.collect(Collectors.toMap(AppUser::getId, Function.identity()));
		return found.stream().collect(Collectors.toMap(Doctor::getId, doctor -> {
			AppUser user = usersById.get(doctor.getUserId());
			return new ConsultationDoctorRef(doctor.getId(), user.getFirstName(), user.getLastName());
		}));
	}

}
