package com.iatros.backend.clinic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateClinicSettingsRequest(
		@NotBlank @Size(max = 64) String timeZone,
		@NotNull @Min(5) @Max(480) Integer defaultAppointmentMinutes,
		@NotNull @Pattern(regexp = "en|fr") String defaultLanguage,
		@NotNull @Min(1) @Max(7) Integer weekStartDay,
		@NotNull Long version) {
}
