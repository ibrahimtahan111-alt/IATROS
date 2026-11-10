package com.iatros.backend.staff;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.web.PageRequests;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.shared.web.SortWhitelist;
import com.iatros.backend.staff.dto.CreateStaffRequest;
import com.iatros.backend.staff.dto.StaffMemberResponse;
import com.iatros.backend.staff.dto.UpdateStaffRequest;
import com.iatros.backend.staff.dto.UpdateStaffStatusRequest;
import com.iatros.backend.user.Role;
import com.iatros.backend.user.UserStatus;

import jakarta.validation.Valid;

/** Staff API (api.md §7). Staff management is owner-only (roles-and-permissions.md §6, §8). */
@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasRole('OWNER')")
class StaffController {

	static final SortWhitelist SORTS = SortWhitelist.of(
			Sort.by("lastName", "firstName"),
			Map.of("lastName", "lastName", "firstName", "firstName", "email", "email", "role", "role",
					"status", "status", "createdAt", "createdAt", "lastLoginAt", "lastLoginAt"));

	private final StaffService staffService;

	StaffController(StaffService staffService) {
		this.staffService = staffService;
	}

	@GetMapping
	PageResponse<StaffMemberResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String search, @RequestParam(required = false) Role role,
			@RequestParam(required = false) UserStatus status, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
		return staffService.list(user, search, role, status, PageRequests.of(page, size, sort, SORTS));
	}

	@GetMapping("/{id}")
	StaffMemberResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return staffService.get(user, id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	StaffMemberResponse create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody CreateStaffRequest request) {
		return staffService.create(user, request);
	}

	@PutMapping("/{id}")
	StaffMemberResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdateStaffRequest request) {
		return staffService.update(user, id, request);
	}

	@PatchMapping("/{id}/status")
	StaffMemberResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdateStaffStatusRequest request) {
		return staffService.changeStatus(user, id, request.status());
	}

	@PostMapping("/{id}/invitation")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void resendInvitation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		staffService.resendInvitation(user, id);
	}

	@PostMapping("/{id}/password-reset")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void requestPasswordReset(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		staffService.requestPasswordReset(user, id);
	}

}
