package com.iatros.backend.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.AppointmentType;
import com.iatros.backend.dev.DevDayPlanner.Booking;
import com.iatros.backend.dev.DevDayPlanner.Note;
import com.iatros.backend.dev.DevDayPlanner.PriorVisit;

/** Who the fictional day continues, and how the visit follows the previous one (decisions.md D-017). */
class DevDayPlannerTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

	private static final UUID MISSED = id(1);

	private static final UUID DUE = id(2);

	private static final UUID RECENT = id(3);

	private static final UUID NEW = id(4);

	private static final UUID THIS_WEEK = id(5);

	@Test
	void continuesThePatientsWhoNeedAVisitAndLetsTheMostRecentRest() {
		List<Booking> bookings = DevDayPlanner.book(List.of(RECENT, DUE, MISSED, NEW, THIS_WEEK), List.of(
				visit(MISSED, AppointmentType.URGENT, AppointmentStatus.NO_SHOW, TODAY.minusDays(3)),
				visit(DUE, AppointmentType.FOLLOW_UP, AppointmentStatus.COMPLETED, TODAY.minusDays(20)),
				visit(RECENT, AppointmentType.ROUTINE, AppointmentStatus.COMPLETED, TODAY.minusDays(1)),
				visit(THIS_WEEK, AppointmentType.ROUTINE, AppointmentStatus.COMPLETED, TODAY.minusDays(3))), TODAY, 4);

		assertThat(bookings).extracting(Booking::patientId).containsExactly(MISSED, DUE, NEW, THIS_WEEK);
		assertThat(bookings.get(0).type()).isEqualTo(AppointmentType.URGENT);
		assertThat(bookings.get(0).reason()).isEqualTo("Rebooked after a missed urgent visit");
		assertThat(bookings.get(0).likelyToMiss()).isTrue();
		assertThat(bookings.get(1).type()).isEqualTo(AppointmentType.ROUTINE);
		assertThat(bookings.get(1).reason()).isEqualTo("Continuing follow-up");
		assertThat(bookings.get(2).type()).isEqualTo(AppointmentType.ROUTINE);
		assertThat(bookings.get(2).reason()).isEqualTo("Routine review");
		assertThat(bookings.get(3).type()).isEqualTo(AppointmentType.FOLLOW_UP);
		assertThat(bookings.get(3).reason()).isEqualTo("Follow-up after the routine review");
	}

	@Test
	void anOlderMissIsRebookedButExpectedToAttend() {
		Booking booking = DevDayPlanner.book(List.of(MISSED),
				List.of(visit(MISSED, AppointmentType.PROCEDURE, AppointmentStatus.NO_SHOW, TODAY.minusDays(8))), TODAY, 1)
			.get(0);

		assertThat(booking.type()).isEqualTo(AppointmentType.PROCEDURE);
		assertThat(booking.reason()).isEqualTo("Rebooked after a missed procedure");
		assertThat(booking.likelyToMiss()).isFalse();
	}

	@Test
	void theLatestVisitWinsWhenAPatientCameBackAfterMissingOne() {
		Booking booking = DevDayPlanner
			.book(List.of(MISSED),
					List.of(visit(MISSED, AppointmentType.URGENT, AppointmentStatus.NO_SHOW, TODAY.minusDays(30)),
							visit(MISSED, AppointmentType.NEW_PATIENT, AppointmentStatus.COMPLETED, TODAY.minusDays(10))),
					TODAY, 1)
			.get(0);

		assertThat(booking.type()).isEqualTo(AppointmentType.FOLLOW_UP);
		assertThat(booking.reason()).isEqualTo("Follow-up after the first visit");
		assertThat(booking.likelyToMiss()).isFalse();
	}

	@Test
	void aCancelledVisitDoesNotCountAsBeingSeen() {
		Booking booking = DevDayPlanner.book(List.of(NEW),
				List.of(visit(NEW, AppointmentType.FOLLOW_UP, AppointmentStatus.CANCELLED, TODAY.minusDays(1))), TODAY, 1)
			.get(0);

		assertThat(booking.reason()).isEqualTo("Routine review");
	}

	@Test
	void stillFillsTheDayWhenEveryoneWasSeenYesterday() {
		List<Booking> bookings = DevDayPlanner.book(List.of(RECENT, DUE), List.of(
				visit(RECENT, AppointmentType.ROUTINE, AppointmentStatus.COMPLETED, TODAY.minusDays(1)),
				visit(DUE, AppointmentType.FOLLOW_UP, AppointmentStatus.COMPLETED, TODAY.minusDays(1))), TODAY, 2);

		assertThat(bookings).extracting(Booking::patientId).containsExactly(RECENT, DUE);
	}

	@Test
	void booksNobodyWhenThereIsNoPanelOrNoRoom() {
		PriorVisit visit = visit(DUE, AppointmentType.ROUTINE, AppointmentStatus.COMPLETED, TODAY.minusDays(30));
		assertThat(DevDayPlanner.book(List.of(), List.of(visit), TODAY, 4)).isEmpty();
		assertThat(DevDayPlanner.book(List.of(DUE), List.of(visit), TODAY, 0)).isEmpty();
	}

	@Test
	void consultationContinuesTheReasonThePatientWasBookedFor() {
		Booking booking = new Booking(DUE, AppointmentType.FOLLOW_UP, "Continuing follow-up", false);

		Note note = DevDayPlanner.note(booking, TODAY);

		assertThat(note.diagnosis()).isEqualTo("Stable since the last visit");
		assertThat(note.clinicalNotes()).isEqualTo("Seen for: Continuing follow-up.");
		assertThat(note.followUpOn()).isEqualTo(TODAY.plusDays(14));

		Note attended = DevDayPlanner.note(
				new Booking(MISSED, AppointmentType.FOLLOW_UP, "Rebooked after a missed follow-up", false), TODAY);
		assertThat(attended.diagnosis()).isEqualTo("Attended after the missed visit");
	}

	private static PriorVisit visit(UUID patientId, AppointmentType type, AppointmentStatus status, LocalDate day) {
		return new PriorVisit(patientId, type, status, day, day.atStartOfDay(ZoneOffset.UTC).toInstant());
	}

	private static UUID id(int n) {
		return UUID.fromString("00000000-0000-7000-8000-00000000000" + n);
	}

}
