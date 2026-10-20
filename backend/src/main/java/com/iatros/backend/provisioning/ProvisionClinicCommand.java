package com.iatros.backend.provisioning;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Command-line entry point for clinic provisioning (decisions.md D-020):
 *
 * <pre>
 * java -jar backend.jar provision-clinic --clinic-name="…" --time-zone=Europe/Paris [--language=fr]
 *      --owner-first-name="…" --owner-last-name="…" --owner-email=… [--owner-specialty=GENERAL_PRACTICE]
 * </pre>
 *
 * In this mode the application starts without the web server and exits when done (see BackendApplication).
 */
@Component
class ProvisionClinicCommand implements ApplicationRunner {

	static final String COMMAND = "provision-clinic";

	private static final Logger log = LoggerFactory.getLogger(ProvisionClinicCommand.class);

	private final ClinicProvisioningService provisioning;

	ProvisionClinicCommand(ClinicProvisioningService provisioning) {
		this.provisioning = provisioning;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!args.getNonOptionArgs().contains(COMMAND)) {
			return;
		}
		UUID clinicId = provisioning.provision(new ClinicProvisioningRequest(option(args, "clinic-name"),
				option(args, "time-zone"), option(args, "language"), option(args, "owner-first-name"),
				option(args, "owner-last-name"), option(args, "owner-email"), option(args, "owner-specialty")));
		log.info("Clinic {} provisioned; the owner has been sent an activation email.", clinicId);
	}

	private static String option(ApplicationArguments args, String name) {
		List<String> values = args.getOptionValues(name);
		return values == null || values.isEmpty() ? null : values.getFirst();
	}

}
