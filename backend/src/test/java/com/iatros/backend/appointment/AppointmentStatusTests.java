package com.iatros.backend.appointment;

import static com.iatros.backend.appointment.AppointmentStatus.CANCELLED;
import static com.iatros.backend.appointment.AppointmentStatus.COMPLETED;
import static com.iatros.backend.appointment.AppointmentStatus.CONFIRMED;
import static com.iatros.backend.appointment.AppointmentStatus.IN_CONSULTATION;
import static com.iatros.backend.appointment.AppointmentStatus.NO_SHOW;
import static com.iatros.backend.appointment.AppointmentStatus.SCHEDULED;
import static com.iatros.backend.appointment.AppointmentStatus.WAITING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The transition table must match decisions.md D-024 exactly. */
class AppointmentStatusTests {

	private static final Map<AppointmentStatus, EnumSet<AppointmentStatus>> D024 = Map.of(
			SCHEDULED, EnumSet.of(CONFIRMED, WAITING, CANCELLED, NO_SHOW),
			CONFIRMED, EnumSet.of(WAITING, CANCELLED, NO_SHOW),
			WAITING, EnumSet.of(IN_CONSULTATION, CANCELLED),
			IN_CONSULTATION, EnumSet.of(COMPLETED),
			COMPLETED, EnumSet.noneOf(AppointmentStatus.class),
			CANCELLED, EnumSet.noneOf(AppointmentStatus.class),
			NO_SHOW, EnumSet.noneOf(AppointmentStatus.class));

	@ParameterizedTest
	@EnumSource(AppointmentStatus.class)
	void allowsExactlyTheDocumentedTransitions(AppointmentStatus from) {
		for (AppointmentStatus to : AppointmentStatus.values()) {
			assertThat(from.canTransitionTo(to))
				.as("%s -> %s", from, to)
				.isEqualTo(D024.get(from).contains(to));
		}
	}

	@Test
	void finalStatusesAreTerminal() {
		assertThat(EnumSet.allOf(AppointmentStatus.class).stream().filter(AppointmentStatus::isTerminal))
			.containsExactlyInAnyOrder(COMPLETED, CANCELLED, NO_SHOW);
	}

	@Test
	void onlyNotYetStartedAppointmentsAreEditable() {
		assertThat(EnumSet.allOf(AppointmentStatus.class).stream().filter(AppointmentStatus::isEditable))
			.containsExactlyInAnyOrder(SCHEDULED, CONFIRMED);
	}

	@Test
	void cancelledAndNoShowDoNotBlockTime() {
		assertThat(EnumSet.allOf(AppointmentStatus.class).stream().filter(s -> !s.blocksTime()))
			.containsExactlyInAnyOrder(CANCELLED, NO_SHOW);
	}

	@Test
	void consultationStatusesRequireAClinicalActor() {
		assertThat(EnumSet.allOf(AppointmentStatus.class).stream().filter(AppointmentStatus::requiresClinicalActor))
			.containsExactlyInAnyOrder(IN_CONSULTATION, COMPLETED);
	}

}
