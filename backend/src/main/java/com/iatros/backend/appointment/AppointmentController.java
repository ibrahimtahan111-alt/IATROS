package com.iatros.backend.appointment;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.appointment.dto.AppointmentResponse;
import com.iatros.backend.appointment.dto.AppointmentSummaryResponse;
import com.iatros.backend.appointment.dto.AvailabilityResponse;
import com.iatros.backend.appointment.dto.CalendarResponse;
import com.iatros.backend.appointment.dto.CancelAppointmentRequest;
import com.iatros.backend.appointment.dto.ChangeAppointmentStatusRequest;
import com.iatros.backend.appointment.dto.CreateAppointmentRequest;
import com.iatros.backend.appointment.dto.RescheduleAppointmentRequest;
import com.iatros.backend.appointment.dto.UpdateAppointmentRequest;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.web.PageRequests;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.shared.web.SortWhitelist;

import jakarta.validation.Valid;

/** Appointment and calendar API (api.md §17–19). Data scope and booking rules are enforced in the services. */
@RestController
@RequestMapping("/api/v1")
class AppointmentController {

	static final SortWhitelist SORTS = SortWhitelist.of(Sort.by(Sort.Direction.DESC, "startAt"),
			Map.of("startAt", "startAt"));

	private final AppointmentService service;
	private final CalendarService calendar;

	AppointmentController(AppointmentService service, CalendarService calendar) {
		this.service = service;
		this.calendar = calendar;
	}

	@GetMapping("/appointments")
	PageResponse<AppointmentSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) UUID doctorId, @RequestParam(required = false) UUID patientId,
			@RequestParam(required = false) List<AppointmentStatus> status,
			@RequestParam(required = false) List<AppointmentType> type,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) List<String> sort) {
		return service.list(user, doctorId, patientId, status, type, from, to,
				PageRequests.of(page, size, sort, SORTS));
	}

	@GetMapping("/appointments/availability")
	AvailabilityResponse availability(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam UUID doctorId,
			@RequestParam LocalDate date, @RequestParam(required = false) Integer duration) {
		return calendar.availability(user, doctorId, date, duration);
	}

	@GetMapping("/calendar/events")
	CalendarResponse events(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam LocalDate from,
			@RequestParam LocalDate to, @RequestParam(required = false) List<UUID> doctorId,
			@RequestParam(required = false) List<AppointmentStatus> status) {
		return calendar.events(user, from, to, doctorId, status);
	}

	@PostMapping("/appointments")
	@ResponseStatus(HttpStatus.CREATED)
	AppointmentResponse create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody CreateAppointmentRequest request) {
		return service.create(user, request);
	}

	@GetMapping("/appointments/{appointmentId}")
	AppointmentResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID appointmentId) {
		return service.get(user, appointmentId);
	}

	@PutMapping("/appointments/{appointmentId}")
	AppointmentResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID appointmentId,
			@Valid @RequestBody UpdateAppointmentRequest request) {
		return service.update(user, appointmentId, request);
	}

	@PatchMapping("/appointments/{appointmentId}/schedule")
	AppointmentResponse reschedule(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID appointmentId,
			@Valid @RequestBody RescheduleAppointmentRequest request) {
		return service.reschedule(user, appointmentId, request);
	}

	@PatchMapping("/appointments/{appointmentId}/status")
	AppointmentResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID appointmentId, @Valid @RequestBody ChangeAppointmentStatusRequest request) {
		return service.changeStatus(user, appointmentId, request);
	}

	@PostMapping("/appointments/{appointmentId}/cancel")
	AppointmentResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID appointmentId,
			@Valid @RequestBody CancelAppointmentRequest request) {
		return service.cancel(user, appointmentId, request);
	}

}
