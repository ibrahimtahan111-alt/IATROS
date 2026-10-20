package com.iatros.backend.schedule.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Effective working hours per date, after exceptions (api.md §20). Times are clinic-local. */
public record WorkingHoursResponse(String timeZone, List<Day> days) {

	public record Day(LocalDate date, List<Range> ranges) {
	}

	public record Range(@JsonFormat(pattern = "HH:mm") LocalTime start, @JsonFormat(pattern = "HH:mm") LocalTime end) {
	}

}
