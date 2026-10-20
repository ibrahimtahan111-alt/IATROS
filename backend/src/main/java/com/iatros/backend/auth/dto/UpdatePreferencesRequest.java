package com.iatros.backend.auth.dto;

import jakarta.validation.constraints.Pattern;

/** @param preferredLanguage {@code en}, {@code fr}, or {@code null} to follow the clinic default language */
public record UpdatePreferencesRequest(@Pattern(regexp = "en|fr") String preferredLanguage) {
}
