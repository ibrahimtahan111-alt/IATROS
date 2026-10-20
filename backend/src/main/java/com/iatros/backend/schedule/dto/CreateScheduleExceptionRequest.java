package com.iatros.backend.schedule.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.iatros.backend.schedule.ScheduleExceptionType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Without times, an UNAVAILABLE exception covers the whole day; EXTRA_AVAILABILITY always needs times (D-026). */
public record CreateScheduleExceptionRequest(
		@NotNull LocalDate date,
		@NotNull ScheduleExceptionType type,
		@JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm") LocalTime endTime,
		@Size(max = 500) String reason) {
}
