package com.iatros.backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.iatros.backend.shared.error.BusinessRuleException;

class AppointmentTests {

	private static final Instant NINE = Instant.parse("2027-04-12T09:00:00Z");
	private static final UUID USER = UUID.randomUUID();

	private Appointment scheduled() {
		return new Appointment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NINE,
				NINE.plus(Duration.ofMinutes(30)), AppointmentType.ROUTINE, null, null, USER);
	}

	@Test
	void startsAsScheduled() {
		assertThat(scheduled().getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
	}

	@Test
	void rejectsEndBeforeStart() {
		assertThatThrownBy(() -> new Appointment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NINE, NINE,
				AppointmentType.ROUTINE, null, null, USER))
			.isInstanceOf(BusinessRuleException.class)
			.extracting("code").isEqualTo("INVALID_TIME_RANGE");
	}

	@Test
	void rejectsAppointmentsLongerThanTwelveHours() {
		assertThatThrownBy(() -> new Appointment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NINE,
				NINE.plus(Duration.ofHours(12).plusMinutes(1)), AppointmentType.ROUTINE, null, null, USER))
			.isInstanceOf(BusinessRuleException.class)
			.extracting("code").isEqualTo("APPOINTMENT_TOO_LONG");
	}

	@Test
	void recordsWhoChangedTheStatusAndWhen() {
		Appointment appointment = scheduled();
		Instant at = NINE.minusSeconds(60);

		appointment.changeStatus(AppointmentStatus.CONFIRMED, at, USER);

		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(appointment.getStatusChangedAt()).isEqualTo(at);
		assertThat(appointment.getStatusChangedBy()).isEqualTo(USER);
	}

	@Test
	void rejectsUndocumentedTransitions() {
		Appointment appointment = scheduled();

		assertThatThrownBy(() -> appointment.changeStatus(AppointmentStatus.COMPLETED, NINE, USER))
			.isInstanceOf(BusinessRuleException.class)
			.extracting("code").isEqualTo("INVALID_STATUS_TRANSITION");
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
	}

	@Test
	void cancellationStoresTheReason() {
		Appointment appointment = scheduled();

		appointment.cancel("Patient request", NINE, USER);

		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(appointment.getCancellationReason()).isEqualTo("Patient request");
	}

	@Test
	void cancelledAppointmentsCannotBeCancelledAgain() {
		Appointment appointment = scheduled();
		appointment.cancel(null, NINE, USER);

		assertThatThrownBy(() -> appointment.cancel("again", NINE, USER))
			.isInstanceOf(BusinessRuleException.class);
		assertThat(appointment.getCancellationReason()).isNull();
	}

	@Test
	void startedAppointmentsCannotBeRescheduled() {
		Appointment appointment = scheduled();
		appointment.changeStatus(AppointmentStatus.WAITING, NINE, USER);

		assertThatThrownBy(() -> appointment.reschedule(appointment.getDoctorId(), NINE.plusSeconds(3600),
				NINE.plusSeconds(5400), USER))
			.isInstanceOf(BusinessRuleException.class)
			.extracting("code").isEqualTo("APPOINTMENT_NOT_EDITABLE");
	}

}
