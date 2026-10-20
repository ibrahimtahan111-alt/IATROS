package com.iatros.backend.kpi;

import java.util.EnumMap;
import java.util.Map;

import com.iatros.backend.appointment.AppointmentStatus;

/**
 * Appointment counts of one doctor (or a sum of doctors) in a period, with the past-dated outcomes that the D-031
 * completion rate is based on.
 */
final class AppointmentTally {

	private final EnumMap<AppointmentStatus, Long> byStatus = new EnumMap<>(AppointmentStatus.class);
	private long pastCompleted;
	private long pastNoShows;
	private long pastCancelled;

	AppointmentTally() {
		for (AppointmentStatus status : AppointmentStatus.values()) {
			byStatus.put(status, 0L);
		}
	}

	void count(AppointmentStatus status, long count) {
		byStatus.merge(status, count, Long::sum);
	}

	void countPast(long completed, long noShows, long cancelled) {
		pastCompleted += completed;
		pastNoShows += noShows;
		pastCancelled += cancelled;
	}

	void add(AppointmentTally other) {
		other.byStatus.forEach(this::count);
		countPast(other.pastCompleted, other.pastNoShows, other.pastCancelled);
	}

	Map<AppointmentStatus, Long> byStatus() {
		return Map.copyOf(byStatus);
	}

	long total() {
		return byStatus.values().stream().mapToLong(Long::longValue).sum();
	}

	long of(AppointmentStatus status) {
		return byStatus.get(status);
	}

	/** COMPLETED / (COMPLETED + NO_SHOW + CANCELLED), of appointments that started before now. */
	Double completionRate() {
		return ratio(pastCompleted, pastCompleted + pastNoShows + pastCancelled);
	}

	/** NO_SHOW / (COMPLETED + NO_SHOW). */
	Double noShowRate() {
		long noShows = of(AppointmentStatus.NO_SHOW);
		return ratio(noShows, of(AppointmentStatus.COMPLETED) + noShows);
	}

	/** CANCELLED / all appointments. */
	Double cancellationRate() {
		return ratio(of(AppointmentStatus.CANCELLED), total());
	}

	private static Double ratio(long part, long whole) {
		return whole == 0 ? null : (double) part / whole;
	}

}
