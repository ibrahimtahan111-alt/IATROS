package com.iatros.backend.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.clinic.ClinicRepository;
import com.iatros.backend.clinic.ClinicSettings;
import com.iatros.backend.clinic.ClinicSettingsRepository;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.shared.i18n.Language;
import com.iatros.backend.shared.mail.EmailMessage;
import com.iatros.backend.shared.mail.EmailSender;
import com.iatros.backend.shared.mail.OutboxEmailSender;
import com.iatros.backend.support.TestClinicFixture;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.Role;
import com.iatros.backend.user.UserStatus;

/** Operator provisioning of a clinic and its owner (decisions.md D-020). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClinicProvisioningServiceTests {

	@Autowired private ClinicProvisioningService provisioning;
	@Autowired private ClinicRepository clinics;
	@Autowired private ClinicSettingsRepository clinicSettings;
	@Autowired private AppUserRepository users;
	@Autowired private DoctorRepository doctors;
	@Autowired private EmailSender emailSender;

	@BeforeEach
	void setUp() {
		((OutboxEmailSender) emailSender).clear();
	}

	@Test
	void createsTheClinicSettingsAndAnInvitedOwner() {
		String email = TestClinicFixture.uniqueEmail("owner");
		UUID clinicId = provisioning.provision(new ClinicProvisioningRequest(" Cabinet Martin ", "Europe/Paris", "fr",
				"Claire", "Martin", email, null));

		assertThat(clinics.findById(clinicId).orElseThrow().getName()).isEqualTo("Cabinet Martin");
		ClinicSettings settings = clinicSettings.findById(clinicId).orElseThrow();
		assertThat(settings.getTimeZone().getId()).isEqualTo("Europe/Paris");
		assertThat(settings.getDefaultLanguage()).isEqualTo(Language.FR);
		AppUser owner = users.findByEmail(email).orElseThrow();
		assertThat(owner.getClinicId()).isEqualTo(clinicId);
		assertThat(owner.getRole()).isEqualTo(Role.OWNER);
		assertThat(owner.getStatus()).isEqualTo(UserStatus.INVITED);
		assertThat(doctors.findByUserId(owner.getId())).isEmpty();
		assertThat(((OutboxEmailSender) emailSender).sentMessages()).singleElement()
			.extracting(EmailMessage::to).isEqualTo(email);
	}

	@Test
	void createsTheOwnersDoctorProfileWhenTheyPractise() {
		String email = TestClinicFixture.uniqueEmail("solo");
		provisioning.provision(new ClinicProvisioningRequest("Solo Practice", "Europe/Athens", null, "Nikos", "Solo",
				email, "GENERAL_PRACTICE"));

		AppUser owner = users.findByEmail(email).orElseThrow();
		assertThat(doctors.findByUserId(owner.getId())).isPresent();
	}

	@Test
	void reportsEveryProblemAndSavesNothing() {
		long clinicsBefore = clinics.count();
		assertThatThrownBy(() -> provisioning.provision(new ClinicProvisioningRequest("", "Mars/Base", "de", "A", " ",
				"not-an-email", "WIZARDRY")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("clinic name is required")
			.hasMessageContaining("owner last name is required")
			.hasMessageContaining("owner email is not a valid address")
			.hasMessageContaining("time zone")
			.hasMessageContaining("language")
			.hasMessageContaining("owner specialty");
		assertThat(clinics.count()).isEqualTo(clinicsBefore);
		assertThat(((OutboxEmailSender) emailSender).sentMessages()).isEmpty();
	}

	@Test
	void refusesAnEmailThatIsAlreadyUsed() {
		String email = TestClinicFixture.uniqueEmail("taken");
		provisioning.provision(new ClinicProvisioningRequest("First", "Europe/Paris", "en", "A", "B", email, null));

		assertThatThrownBy(() -> provisioning.provision(new ClinicProvisioningRequest("Second", "Europe/Paris", "en",
				"C", "D", email.toUpperCase(), null)))
			.hasMessageContaining("already used");
	}

}
