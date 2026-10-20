package com.iatros.backend.clinic;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Calendar dates are clinic-local (decisions.md D-004): "today" is today in the clinic's time zone. */
@Component
@Transactional(readOnly = true)
public class ClinicTime {

	private final ClinicSettingsRepository clinicSettings;
	private final Clock clock;

	ClinicTime(ClinicSettingsRepository clinicSettings, Clock clock) {
		this.clinicSettings = clinicSettings;
		this.clock = clock;
	}

	public ZoneId zone(UUID clinicId) {
		return clinicSettings.findById(clinicId)
			.map(ClinicSettings::getTimeZone)
			.orElseThrow(() -> new IllegalStateException("Clinic settings missing for " + clinicId));
	}

	public LocalDate today(UUID clinicId) {
		return LocalDate.now(clock.withZone(zone(clinicId)));
	}

}
