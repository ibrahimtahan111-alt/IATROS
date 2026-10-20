package com.iatros.backend.appointment.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.AppointmentType;

/**
 * One appointment with what the caller may do with it (decisions.md D-038): {@code editable} covers details and
 * rescheduling, {@code nextStatuses} the status changes allowed to the caller (cancellation excluded; see
 * {@code cancellable}). {@code consultationId} is only sent to callers with clinical access.
 */
public record AppointmentResponse(UUID id, NameRef patient, NameRef doctor, Instant startAt, Instant endAt,
		AppointmentType type, AppointmentStatus status, String reason, String notes, String cancellationReason,
		Instant statusChangedAt, NameRef createdBy, Instant createdAt, Instant updatedAt, boolean editable,
		boolean cancellable, List<AppointmentStatus> nextStatuses, UUID consultationId, long version) {
}
