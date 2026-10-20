package com.iatros.backend.schedule.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.iatros.backend.schedule.ScheduleExceptionType;

/** Times are null for an all-day exception (only possible for UNAVAILABLE). */
public record ScheduleExceptionResponse(UUID id, LocalDate date, ScheduleExceptionType type,
		@JsonFormat(pattern = "HH:mm") LocalTime startTime, @JsonFormat(pattern = "HH:mm") LocalTime endTime,
		String reason, Instant createdAt) {
}
