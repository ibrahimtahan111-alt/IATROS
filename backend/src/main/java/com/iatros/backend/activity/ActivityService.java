package com.iatros.backend.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.access.AccessPolicy;
import com.iatros.backend.activity.dto.ActivityItemResponse;
import com.iatros.backend.appointment.AppointmentDirectory;
import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.dto.NameRef;
import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditEvent;
import com.iatros.backend.audit.AuditEventRepository;
import com.iatros.backend.clinic.ClinicTime;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.patient.PatientDoctor;
import com.iatros.backend.patient.PatientDoctorRepository;
import com.iatros.backend.patient.PatientRepository;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ForbiddenOperationException;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;

/**
 * The clinic activity feed (api.md §24, decisions.md D-039), read from the audit trail. Owners see the whole clinic,
 * optionally narrowed to one doctor; doctors see only their own activity; secretaries have no feed. Patient names
 * are shown only for patients the caller may see.
 */
@Service
@Transactional(readOnly = true)
public class ActivityService {

	private static final Instant EARLIEST = Instant.parse("0001-01-01T00:00:00Z");
	private static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");

	private final AuditEventRepository events;
	private final AccessPolicy access;
	private final ClinicTime clinicTime;
	private final AppointmentDirectory directory;
	private final PatientRepository patients;
	private final PatientDoctorRepository patientDoctors;

	ActivityService(AuditEventRepository events, AccessPolicy access, ClinicTime clinicTime,
			AppointmentDirectory directory, PatientRepository patients, PatientDoctorRepository patientDoctors) {
		this.events = events;
		this.access = access;
		this.clinicTime = clinicTime;
		this.directory = directory;
		this.patients = patients;
		this.patientDoctors = patientDoctors;
	}

	/** {@code from}/{@code to} are clinic-local dates, both inclusive. Empty filters mean "all". */
	public PageResponse<ActivityItemResponse> list(AuthenticatedUser caller, LocalDate from, LocalDate to,
			Collection<ActivityCategory> categories, UUID doctorId, Pageable pageable) {
		if (from != null && to != null && to.isBefore(from)) {
			throw ValidationException.of("to", "END_BEFORE_START");
		}
		Doctor doctor = scopedDoctor(caller, doctorId);
		ZoneId zone = clinicTime.zone(caller.clinicId());
		Page<AuditEvent> page = find(caller, doctor, ActivityCategory.actionsOf(categories),
				from == null ? EARLIEST : from.atStartOfDay(zone).toInstant(),
				to == null ? LATEST : to.plusDays(1).atStartOfDay(zone).toInstant(), pageable);
		return new PageResponse<>(toItems(caller, page.getContent()), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	/**
	 * The latest activity, for dashboards: of one doctor, or of the whole clinic when {@code doctorId} is null
	 * (owners only; doctors always get their own).
	 */
	public List<ActivityItemResponse> recent(AuthenticatedUser caller, UUID doctorId, int limit) {
		Page<AuditEvent> page = find(caller, scopedDoctor(caller, doctorId), ActivityCategory.actionsOf(null),
				EARLIEST, LATEST, PageRequest.of(0, limit));
		return toItems(caller, page.getContent());
	}

	private Page<AuditEvent> find(AuthenticatedUser caller, Doctor doctor, List<String> actions, Instant from,
			Instant to, Pageable pageable) {
		if (doctor == null) {
			return events.findClinicActivity(caller.clinicId(), actions, from, to, pageable);
		}
		return events.findDoctorActivity(caller.clinicId(), doctor.getUserId(), doctor.getId(),
				doctor.getId().toString(), actions, from, to, pageable);
	}

	/** Null for the whole clinic (owners only); otherwise the doctor whose activity is shown. */
	private Doctor scopedDoctor(AuthenticatedUser caller, UUID requested) {
		if (caller.hasRole(Role.SECRETARY)) {
			throw new ForbiddenOperationException("FORBIDDEN", "Operation not permitted for this role");
		}
		if (caller.hasRole(Role.DOCTOR)) {
			UUID own = access.dataScope(caller).doctorId().orElseThrow();
			return access.requireDoctorManagement(caller, requested == null ? own : requested);
		}
		return requested == null ? null : access.requireDoctorManagement(caller, requested);
	}

	private List<ActivityItemResponse> toItems(AuthenticatedUser caller, List<AuditEvent> found) {
		Set<UUID> userIds = new HashSet<>();
		Set<UUID> patientIds = new HashSet<>();
		Set<UUID> doctorIds = new HashSet<>();
		for (AuditEvent event : found) {
			addIfPresent(userIds, event.getActorUserId());
			addIfPresent(userIds, staffIdOf(event));
			addIfPresent(patientIds, patientIdOf(event));
			addIfPresent(doctorIds, doctorIdOf(event));
		}
		Map<UUID, NameRef> users = directory.usersById(userIds)
			.values()
			.stream()
			.filter(user -> caller.clinicId().equals(user.getClinicId()))
			.collect(Collectors.toMap(AppUser::getId,
					user -> new NameRef(user.getId(), user.getFirstName(), user.getLastName())));
		Map<UUID, NameRef> visiblePatients = visiblePatients(caller, patientIds);
		Map<UUID, NameRef> doctors = directory.doctors(doctorIds);

		return found.stream()
			.map(event -> new ActivityItemResponse(event.getId(), event.getOccurredAt(), event.getAction(),
					ActivityCategory.of(event.getAction()).orElseThrow(), refOf(users, event.getActorUserId()),
					event.getEntityType(), refOf(visiblePatients, patientIdOf(event)),
					refOf(doctors, doctorIdOf(event)), refOf(users, staffIdOf(event)),
					AuditActions.ENTITY_APPOINTMENT.equals(event.getEntityType()) ? event.getEntityId() : null,
					statusOf(event)))
			.toList();
	}

	private Map<UUID, NameRef> visiblePatients(AuthenticatedUser caller, Set<UUID> patientIds) {
		if (patientIds.isEmpty()) {
			return Map.of();
		}
		Collection<UUID> visible = patientIds;
		if (caller.hasRole(Role.DOCTOR)) {
			UUID own = access.dataScope(caller).doctorId().orElseThrow();
			visible = patientDoctors.findAllByDoctorIdAndPatientIdIn(own, patientIds)
				.stream()
				.map(PatientDoctor::getPatientId)
				.toList();
		}
		if (visible.isEmpty()) {
			return Map.of();
		}
		return patients.findAllByClinicIdAndIdIn(caller.clinicId(), visible)
			.stream()
			.collect(Collectors.toMap(patient -> patient.getId(),
					patient -> new NameRef(patient.getId(), patient.getFirstName(), patient.getLastName())));
	}

	private static UUID patientIdOf(AuditEvent event) {
		return AuditActions.ENTITY_PATIENT.equals(event.getEntityType()) ? event.getEntityId()
				: uuid(event.getMetadata().get("patientId"));
	}

	private static UUID doctorIdOf(AuditEvent event) {
		return AuditActions.ENTITY_DOCTOR.equals(event.getEntityType()) ? event.getEntityId()
				: uuid(event.getMetadata().get("doctorId"));
	}

	private static UUID staffIdOf(AuditEvent event) {
		return AuditActions.ENTITY_USER.equals(event.getEntityType()) ? event.getEntityId() : null;
	}

	private static AppointmentStatus statusOf(AuditEvent event) {
		if (!AuditActions.APPOINTMENT_STATUS_CHANGED.equals(event.getAction())) {
			return null;
		}
		Object to = event.getMetadata().get("to");
		return to == null ? null : AppointmentStatus.valueOf(to.toString());
	}

	private static UUID uuid(Object value) {
		return value == null ? null : UUID.fromString(value.toString());
	}

	private static NameRef refOf(Map<UUID, NameRef> refs, UUID id) {
		return id == null ? null : refs.get(id);
	}

	private static void addIfPresent(Set<UUID> ids, UUID id) {
		if (Objects.nonNull(id)) {
			ids.add(id);
		}
	}

}
