package com.iatros.backend;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	/** Must match ProvisionClinicCommand.COMMAND. */
	private static final String PROVISION_COMMAND = "provision-clinic";

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(BackendApplication.class);
		if (Arrays.asList(args).contains(PROVISION_COMMAND)) {
			// One-shot operator command: no web server, exit when the command has run.
			application.setWebApplicationType(WebApplicationType.NONE);
			System.exit(SpringApplication.exit(application.run(args)));
		}
		application.run(args);
	}

}
