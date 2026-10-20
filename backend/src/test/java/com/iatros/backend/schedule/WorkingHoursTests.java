package com.iatros.backend.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Working-hours composition (decisions.md D-035): weekly periods, extra availability, then unavailability. */
class WorkingHoursTests {

	private static final UUID DOCTOR = UUID.randomUUID();
	private static final LocalDate DATE = LocalDate.of(2030, 3, 4);

	@Test
	void rejectsEmptyOrInvertedRanges() {
		assertThatIllegalArgumentException().isThrownBy(() -> range("10:00", "10:00"));
		assertThatIllegalArgumentException().isThrownBy(() -> range("11:00", "10:00"));
	}

	@Test
	void unionMergesOverlappingAndTouchingRangesInOrder() {
		assertThat(TimeRange.union(List.of(range("14:00", "18:00"), range("09:00", "12:00"), range("12:00", "13:00"),
				range("10:00", "11:00"))))
			.containsExactly(range("09:00", "13:00"), range("14:00", "18:00"));
	}

	@Test
	void subtractSplitsTrimsAndRemovesRanges() {
		List<TimeRange> open = List.of(range("09:00", "13:00"), range("14:00", "18:00"));
		assertThat(TimeRange.subtract(open, List.of(range("10:00", "11:00"))))
			.containsExactly(range("09:00", "10:00"), range("11:00", "13:00"), range("14:00", "18:00"));
		assertThat(TimeRange.subtract(open, List.of(range("12:00", "15:00"))))
			.containsExactly(range("09:00", "12:00"), range("15:00", "18:00"));
		assertThat(TimeRange.subtract(open, List.of(range("08:00", "19:00")))).isEmpty();
		assertThat(TimeRange.subtract(open, List.of(range("13:00", "14:00")))).isEqualTo(open);
	}

	@Test
	void containsIsHalfOpen() {
		TimeRange morning = range("09:00", "12:00");
		assertThat(morning.contains(LocalTime.parse("09:00"), LocalTime.parse("12:00"))).isTrue();
		assertThat(morning.contains(LocalTime.parse("11:30"), LocalTime.parse("12:15"))).isFalse();
		assertThat(morning.overlaps(range("12:00", "13:00"))).isFalse();
	}

	@Test
	void extraAvailabilityExtendsAndUnavailabilityCutsTheDay() {
		List<TimeRange> weekly = List.of(range("09:00", "12:00"), range("14:00", "18:00"));
		List<TimeRange> hours = WorkingHoursService.hoursFor(weekly, List.of(
				ScheduleException.extraAvailability(DOCTOR, DATE, time("12:00"), time("13:00"), null, null),
				ScheduleException.unavailable(DOCTOR, DATE, time("15:00"), time("16:00"), null, null)));
		assertThat(hours).containsExactly(range("09:00", "13:00"), range("14:00", "15:00"), range("16:00", "18:00"));
	}

	@Test
	void unavailabilityWinsOverExtraAvailability() {
		List<TimeRange> hours = WorkingHoursService.hoursFor(List.of(), List.of(
				ScheduleException.extraAvailability(DOCTOR, DATE, time("09:00"), time("12:00"), null, null),
				ScheduleException.unavailable(DOCTOR, DATE, time("09:00"), time("10:00"), null, null)));
		assertThat(hours).containsExactly(range("10:00", "12:00"));
	}

	@Test
	void anAllDayUnavailabilityClosesTheWholeDay() {
		List<TimeRange> hours = WorkingHoursService.hoursFor(List.of(range("09:00", "18:00")), List.of(
				ScheduleException.extraAvailability(DOCTOR, DATE, time("18:00"), time("20:00"), null, null),
				ScheduleException.unavailableAllDay(DOCTOR, DATE, null, null)));
		assertThat(hours).isEmpty();
	}

	@Test
	void extraAvailabilityOpensADayWithoutWeeklyHours() {
		assertThat(WorkingHoursService.hoursFor(List.of(), List.of(
				ScheduleException.extraAvailability(DOCTOR, DATE, time("08:00"), time("10:00"), null, null))))
			.containsExactly(range("08:00", "10:00"));
	}

	private static TimeRange range(String start, String end) {
		return new TimeRange(time(start), time(end));
	}

	private static LocalTime time(String value) {
		return LocalTime.parse(value);
	}

}
