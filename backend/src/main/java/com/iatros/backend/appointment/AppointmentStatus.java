package com.iatros.backend.appointment;

import java.util.EnumSet;
import java.util.Set;

/** Appointment lifecycle (decisions.md D-024). Values are stored as-is and never translated. */
public enum AppointmentStatus {
	SCHEDULED,
	CONFIRMED,
	WAITING,
	IN_CONSULTATION,
	COMPLETED,
	CANCELLED,
	NO_SHOW;

	public Set<AppointmentStatus> allowedNextStatuses() {
		return switch (this) {
			case SCHEDULED -> EnumSet.of(CONFIRMED, WAITING, CANCELLED, NO_SHOW);
			case CONFIRMED -> EnumSet.of(WAITING, CANCELLED, NO_SHOW);
			case WAITING -> EnumSet.of(IN_CONSULTATION, CANCELLED);
			case IN_CONSULTATION -> EnumSet.of(COMPLETED);
			case COMPLETED, CANCELLED, NO_SHOW -> EnumSet.noneOf(AppointmentStatus.class);
		};
	}

	public boolean canTransitionTo(AppointmentStatus next) {
		return allowedNextStatuses().contains(next);
	}

	public boolean isTerminal() {
		return allowedNextStatuses().isEmpty();
	}

	/** Only not-yet-started appointments may be rescheduled or edited. */
	public boolean isEditable() {
		return this == SCHEDULED || this == CONFIRMED;
	}

	/** Statuses that occupy the doctor's time; must match the appointment_no_overlap constraint. */
	public boolean blocksTime() {
		return this != CANCELLED && this != NO_SHOW;
	}

	/** Entering this status may only be done by the owner or the appointment's doctor. */
	public boolean requiresClinicalActor() {
		return this == IN_CONSULTATION || this == COMPLETED;
	}

}
