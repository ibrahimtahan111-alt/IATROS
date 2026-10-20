package com.iatros.backend.schedule.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replaces the whole weekly schedule. An empty list means no regular working hours. */
public record UpdateScheduleRequest(
		@NotNull @Size(max = 70) List<@NotNull @Valid SchedulePeriodDto> periods,
		@NotNull Long version) {
}
