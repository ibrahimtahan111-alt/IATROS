package com.iatros.backend.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.iatros.backend.shared.persistence.TimestampedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One-off change to a doctor's weekly schedule (decisions.md D-026).
 * Without start/end time it covers the whole day; EXTRA_AVAILABILITY always has times.
 */
@Entity
@Table(name = "schedule_exception")
public class ScheduleException extends TimestampedEntity {

	@Column(name = "doctor_id", nullable = false, updatable = false)
	private UUID doctorId;

	@Column(name = "exception_date", nullable = false)
	private LocalDate exceptionDate;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ScheduleExceptionType type;

	@Column(length = 500)
	private String reason;

	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	protected ScheduleException() {
	}

	private ScheduleException(UUID doctorId, LocalDate exceptionDate, LocalTime startTime, LocalTime endTime,
			ScheduleExceptionType type, String reason, UUID createdBy) {
		if ((startTime == null) != (endTime == null)) {
			throw new IllegalArgumentException("startTime and endTime must be set together");
		}
		if (startTime != null && !endTime.isAfter(startTime)) {
			throw new IllegalArgumentException("endTime must be after startTime");
		}
		if (type == ScheduleExceptionType.EXTRA_AVAILABILITY && startTime == null) {
			throw new IllegalArgumentException("Extra availability requires a time range");
		}
		this.doctorId = doctorId;
		this.exceptionDate = exceptionDate;
		this.startTime = startTime;
		this.endTime = endTime;
		this.type = type;
		this.reason = reason;
		this.createdBy = createdBy;
	}

	public static ScheduleException unavailableAllDay(UUID doctorId, LocalDate date, String reason, UUID createdBy) {
		return new ScheduleException(doctorId, date, null, null, ScheduleExceptionType.UNAVAILABLE, reason, createdBy);
	}

	public static ScheduleException unavailable(UUID doctorId, LocalDate date, LocalTime start, LocalTime end,
			String reason, UUID createdBy) {
		return new ScheduleException(doctorId, date, start, end, ScheduleExceptionType.UNAVAILABLE, reason, createdBy);
	}

	public static ScheduleException extraAvailability(UUID doctorId, LocalDate date, LocalTime start, LocalTime end,
			String reason, UUID createdBy) {
		return new ScheduleException(doctorId, date, start, end, ScheduleExceptionType.EXTRA_AVAILABILITY, reason,
				createdBy);
	}

	public boolean isAllDay() {
		return startTime == null;
	}

	public UUID getDoctorId() {
		return doctorId;
	}

	public LocalDate getExceptionDate() {
		return exceptionDate;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public ScheduleExceptionType getType() {
		return type;
	}

	public String getReason() {
		return reason;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

}
