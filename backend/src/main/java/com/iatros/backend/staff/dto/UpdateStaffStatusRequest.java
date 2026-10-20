package com.iatros.backend.staff.dto;

import com.iatros.backend.user.UserStatus;

import jakarta.validation.constraints.NotNull;

/** {@code ACTIVE} reactivates, {@code DEACTIVATED} deactivates. {@code INVITED} cannot be requested. */
public record UpdateStaffStatusRequest(@NotNull UserStatus status) {
}
