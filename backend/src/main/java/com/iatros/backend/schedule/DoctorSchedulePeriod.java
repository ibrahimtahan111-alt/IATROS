package com.iatros.backend.schedule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.iatros.backend.shared.persistence.TimestampedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A recurring weekly working period. Times are local to the clinic's time zone (decisions.md D-004). */
@Entity
@Table(name = "doctor_schedule_period")
public class DoctorSchedulePeriod extends TimestampedEntity {

	@Column(name = "doctor_id", nullable = false, updatable = false)
	private UUID doctorId;

	/** ISO-8601 day number: 1 = Monday … 7 = Sunday. */
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "day_of_week", nullable = false)
	private int dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	protected DoctorSchedulePeriod() {
	}

	public DoctorSchedulePeriod(UUID doctorId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
		if (!endTime.isAfter(startTime)) {
			throw new IllegalArgumentException("endTime must be after startTime");
		}
		this.doctorId = doctorId;
		this.dayOfWeek = dayOfWeek.getValue();
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public boolean covers(LocalTime start, LocalTime end) {
		return !start.isBefore(startTime) && !end.isAfter(endTime);
	}

	public UUID getDoctorId() {
		return doctorId;
	}

	public DayOfWeek getDayOfWeek() {
		return DayOfWeek.of(dayOfWeek);
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

}
