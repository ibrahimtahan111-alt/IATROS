package com.iatros.backend.dev;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Fills today after the one-time demo seed ({@link DevDataSeeder} is order 0). */
@Component
@Profile("dev")
@Order(100)
class DevDailyClinicRunner implements ApplicationRunner {

	private final DevDailyClinic clinic;

	DevDailyClinicRunner(DevDailyClinic clinic) {
		this.clinic = clinic;
	}

	@Override
	public void run(ApplicationArguments args) {
		clinic.fillToday();
	}

}
