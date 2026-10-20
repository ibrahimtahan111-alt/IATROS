package com.iatros.backend.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.schedule.dto.CreateScheduleExceptionRequest;
import com.iatros.backend.schedule.dto.ScheduleExceptionResponse;
import com.iatros.backend.schedule.dto.UpdateScheduleRequest;
import com.iatros.backend.schedule.dto.WeeklyScheduleResponse;
import com.iatros.backend.schedule.dto.WorkingHoursResponse;
import com.iatros.backend.security.AuthenticatedUser;

import jakarta.validation.Valid;

/** Doctor schedule API (api.md §20). Role and data-scope checks are in {@link ScheduleService}. */
@RestController
@RequestMapping("/api/v1/doctors/{doctorId}")
class ScheduleController {

	private final ScheduleService scheduleService;

	ScheduleController(ScheduleService scheduleService) {
		this.scheduleService = scheduleService;
	}

	@GetMapping("/schedule")
	WeeklyScheduleResponse getSchedule(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID doctorId) {
		return scheduleService.getWeekly(user, doctorId);
	}

	@PutMapping("/schedule")
	WeeklyScheduleResponse replaceSchedule(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID doctorId, @Valid @RequestBody UpdateScheduleRequest request) {
		return scheduleService.replaceWeekly(user, doctorId, request);
	}

	@GetMapping("/schedule/exceptions")
	List<ScheduleExceptionResponse> listExceptions(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID doctorId, @RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		return scheduleService.listExceptions(user, doctorId, from, to);
	}

	@PostMapping("/schedule/exceptions")
	@ResponseStatus(HttpStatus.CREATED)
	ScheduleExceptionResponse createException(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID doctorId, @Valid @RequestBody CreateScheduleExceptionRequest request) {
		return scheduleService.createException(user, doctorId, request);
	}

	@DeleteMapping("/schedule/exceptions/{exceptionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteException(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID doctorId,
			@PathVariable UUID exceptionId) {
		scheduleService.deleteException(user, doctorId, exceptionId);
	}

	@GetMapping("/working-hours")
	WorkingHoursResponse workingHours(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID doctorId,
			@RequestParam LocalDate from, @RequestParam LocalDate to) {
		return scheduleService.workingHours(user, doctorId, from, to);
	}

}
