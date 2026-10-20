package com.iatros.backend.schedule.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** A weekly working period. {@code dayOfWeek} is ISO-8601 (1 = Monday … 7 = Sunday); times are clinic-local. */
public record SchedulePeriodDto(
		@NotNull @Min(1) @Max(7) Integer dayOfWeek,
		@NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
}
