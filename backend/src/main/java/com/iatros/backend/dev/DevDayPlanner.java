package com.iatros.backend.dev;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.AppointmentType;

/**
 * Chooses one clinic day's fictional visits from patients the clinic already has. The choice follows the last
 * completed or missed visit, so a new day continues an existing story (decisions.md D-017).
 */
final class DevDayPlanner {

	/** A completed visit at least this old is due for another appointment. */
	static final int DUE_AFTER_DAYS = 7;

	private DevDayPlanner() {
	}

	record PriorVisit(UUID patientId, AppointmentType type, AppointmentStatus status, LocalDate day, Instant start) {
	}

	record Booking(UUID patientId, AppointmentType type, String reason, boolean likelyToMiss) {
	}

	record Note(String diagnosis, String clinicalNotes, String treatmentNotes, String followUpNotes, LocalDate followUpOn) {
	}

	/**
	 * @param patientsInOrder active primary patients, in a stable order
	 * @param slots how many of them to book; the most recently seen are left to rest when the panel is larger
	 */
	static List<Booking> book(List<UUID> patientsInOrder, List<PriorVisit> history, LocalDate today, int slots) {
		List<UUID> patients = distinct(patientsInOrder);
		if (slots <= 0 || patients.isEmpty()) {
			return List.of();
		}
		Map<UUID, PriorVisit> latest = latestMeaningfulVisit(history);
		Map<UUID, Integer> order = new HashMap<>();
		for (int i = 0; i < patients.size(); i++) {
			order.put(patients.get(i), i);
		}
		List<UUID> ranked = new ArrayList<>(patients);
		ranked.sort((left, right) -> compare(latest.get(left), latest.get(right), today, order.get(left), order.get(right)));

		List<UUID> chosen = new ArrayList<>();
		for (UUID patientId : ranked) {
			if (rank(latest.get(patientId), today) < 4) {
				chosen.add(patientId);
			}
		}
		if (chosen.size() < Math.min(slots, patients.size())) {
			for (UUID patientId : ranked) {
				if (!chosen.contains(patientId)) {
					chosen.add(patientId);
				}
			}
		}
		if (chosen.size() > slots) {
			chosen = new ArrayList<>(chosen.subList(0, slots));
		}

		List<Booking> bookings = new ArrayList<>();
		for (UUID patientId : chosen) {
			PriorVisit visit = latest.get(patientId);
			bookings.add(new Booking(patientId, nextType(visit), reason(visit), likelyToMiss(visit, today)));
		}
		return List.copyOf(bookings);
	}

	/** A short consultation that continues the reason the patient was booked for. */
	static Note note(Booking booking, LocalDate today) {
		String diagnosis = booking.reason().startsWith("Rebooked") ? "Attended after the missed visit" : switch (booking.type()) {
			case URGENT -> "Acute problem, improving";
			case PROCEDURE -> "Procedure completed as planned";
			case FOLLOW_UP -> "Stable since the last visit";
			case NEW_PATIENT -> "First assessment recorded";
			default -> "Routine review, no new concern";
		};
		int reviewInDays = booking.type() == AppointmentType.ROUTINE ? 28 : 14;
		return new Note(diagnosis, "Seen for: " + booking.reason() + ".",
				"Continue the current plan.", "Review if symptoms change.", today.plusDays(reviewInDays));
	}

	private static List<UUID> distinct(List<UUID> patients) {
		List<UUID> distinct = new ArrayList<>();
		for (UUID patientId : patients) {
			if (patientId != null && !distinct.contains(patientId)) {
				distinct.add(patientId);
			}
		}
		return distinct;
	}

	private static Map<UUID, PriorVisit> latestMeaningfulVisit(List<PriorVisit> history) {
		Map<UUID, PriorVisit> latest = new HashMap<>();
		for (PriorVisit visit : history) {
			if (visit.status() != AppointmentStatus.COMPLETED && visit.status() != AppointmentStatus.NO_SHOW) {
				continue;
			}
			PriorVisit existing = latest.get(visit.patientId());
			if (existing == null || visit.start().isAfter(existing.start())) {
				latest.put(visit.patientId(), visit);
			}
		}
		return latest;
	}

	private static int compare(PriorVisit left, PriorVisit right, LocalDate today, int leftOrder, int rightOrder) {
		int byRank = Integer.compare(rank(left, today), rank(right, today));
		if (byRank != 0) {
			return byRank;
		}
		if (left == null || right == null) {
			return Integer.compare(leftOrder, rightOrder);
		}
		int byDay = left.day().compareTo(right.day());
		if (rank(left, today) == 0) {
			byDay = -byDay;
		}
		if (byDay != 0) {
			return byDay;
		}
		return Integer.compare(leftOrder, rightOrder);
	}

	/** 0 missed, 1 due, 2 never seen, 3 seen earlier this week, 4 seen yesterday or today. */
	private static int rank(PriorVisit visit, LocalDate today) {
		if (visit == null) {
			return 2;
		}
		if (visit.status() == AppointmentStatus.NO_SHOW) {
			return 0;
		}
		if (visit.status() == AppointmentStatus.COMPLETED && visit.day().isBefore(today.minusDays(DUE_AFTER_DAYS - 1))) {
			return 1;
		}
		if (!visit.day().isBefore(today.minusDays(1))) {
			return 4;
		}
		return 3;
	}

	private static boolean likelyToMiss(PriorVisit visit, LocalDate today) {
		return visit != null && visit.status() == AppointmentStatus.NO_SHOW
				&& !visit.day().isBefore(today.minusDays(DUE_AFTER_DAYS));
	}

	private static AppointmentType nextType(PriorVisit visit) {
		if (visit == null) {
			return AppointmentType.ROUTINE;
		}
		if (visit.status() == AppointmentStatus.NO_SHOW) {
			return visit.type();
		}
		return switch (visit.type()) {
			case NEW_PATIENT, URGENT, PROCEDURE, ROUTINE -> AppointmentType.FOLLOW_UP;
			case FOLLOW_UP, OTHER -> AppointmentType.ROUTINE;
		};
	}

	private static String reason(PriorVisit visit) {
		if (visit == null) {
			return "Routine review";
		}
		if (visit.status() == AppointmentStatus.NO_SHOW) {
			return "Rebooked after a missed " + label(visit.type());
		}
		return switch (visit.type()) {
			case NEW_PATIENT -> "Follow-up after the first visit";
			case FOLLOW_UP -> "Continuing follow-up";
			case URGENT -> "Review after the urgent visit";
			case PROCEDURE -> "Review after the procedure";
			case ROUTINE -> "Follow-up after the routine review";
			case OTHER -> "Continuing the last consultation";
		};
	}

	private static String label(AppointmentType type) {
		return switch (type) {
			case NEW_PATIENT -> "first visit";
			case FOLLOW_UP -> "follow-up";
			case ROUTINE -> "routine visit";
			case PROCEDURE -> "procedure";
			case URGENT -> "urgent visit";
			case OTHER -> "visit";
		};
	}

}
