package com.iatros.backend.appointment;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.iatros.backend.shared.error.BusinessRuleException;
import com.iatros.backend.shared.persistence.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A scheduled slot for a patient with a doctor. Availability and conflicts are checked by the service;
 * the appointment_no_overlap constraint is the final guarantee (decisions.md D-005).
 */
@Entity
@Table(name = "appointment")
public class Appointment extends AuditedEntity {

	public static final Duration MAX_DURATION = Duration.ofHours(12);

	@Column(name = "clinic_id", nullable = false, updatable = false)
	private UUID clinicId;

	@Column(name = "patient_id", nullable = false, updatable = false)
	private UUID patientId;

	@Column(name = "doctor_id", nullable = false)
	private UUID doctorId;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at", nullable = false)
	private Instant endAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "appointment_type", nullable = false, length = 20)
	private AppointmentType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AppointmentStatus status = AppointmentStatus.SCHEDULED;

	@Column(length = 500)
	private String reason;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(name = "cancellation_reason", length = 500)
	private String cancellationReason;

	@Column(name = "status_changed_at")
	private Instant statusChangedAt;

	@Column(name = "status_changed_by")
	private UUID statusChangedBy;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "updated_by")
	private UUID updatedBy;

	protected Appointment() {
	}

	public Appointment(UUID clinicId, UUID patientId, UUID doctorId, Instant startAt, Instant endAt,
			AppointmentType type, String reason, String notes, UUID createdBy) {
		requireValidTimes(startAt, endAt);
		this.clinicId = clinicId;
		this.patientId = patientId;
		this.doctorId = doctorId;
		this.startAt = startAt;
		this.endAt = endAt;
		this.type = type;
		this.reason = reason;
		this.notes = notes;
		this.createdBy = createdBy;
	}

	public void reschedule(UUID doctorId, Instant startAt, Instant endAt, UUID by) {
		requireEditable();
		requireValidTimes(startAt, endAt);
		this.doctorId = doctorId;
		this.startAt = startAt;
		this.endAt = endAt;
		this.updatedBy = by;
	}

	public void updateDetails(AppointmentType type, String reason, String notes, UUID by) {
		requireEditable();
		this.type = type;
		this.reason = reason;
		this.notes = notes;
		this.updatedBy = by;
	}

	/** Changes status following D-024. Use {@link #cancel} to cancel with a reason. */
	public void changeStatus(AppointmentStatus next, Instant at, UUID by) {
		if (!status.canTransitionTo(next)) {
			throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
					"Cannot change appointment status from " + status + " to " + next);
		}
		this.status = next;
		this.statusChangedAt = at;
		this.statusChangedBy = by;
		this.updatedBy = by;
	}

	public void cancel(String cancellationReason, Instant at, UUID by) {
		changeStatus(AppointmentStatus.CANCELLED, at, by);
		this.cancellationReason = cancellationReason;
	}

	private void requireEditable() {
		if (!status.isEditable()) {
			throw new BusinessRuleException("APPOINTMENT_NOT_EDITABLE",
					"Appointment in status " + status + " cannot be edited");
		}
	}

	static void requireValidTimes(Instant startAt, Instant endAt) {
		if (!endAt.isAfter(startAt)) {
			throw new BusinessRuleException("INVALID_TIME_RANGE", "endAt must be after startAt");
		}
		if (Duration.between(startAt, endAt).compareTo(MAX_DURATION) > 0) {
			throw new BusinessRuleException("APPOINTMENT_TOO_LONG", "Appointment exceeds maximum duration");
		}
	}

	public UUID getClinicId() {
		return clinicId;
	}

	public UUID getPatientId() {
		return patientId;
	}

	public UUID getDoctorId() {
		return doctorId;
	}

	public Instant getStartAt() {
		return startAt;
	}

	public Instant getEndAt() {
		return endAt;
	}

	public AppointmentType getType() {
		return type;
	}

	public AppointmentStatus getStatus() {
		return status;
	}

	public String getReason() {
		return reason;
	}

	public String getNotes() {
		return notes;
	}

	public String getCancellationReason() {
		return cancellationReason;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}

	public UUID getStatusChangedBy() {
		return statusChangedBy;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public UUID getUpdatedBy() {
		return updatedBy;
	}

}
