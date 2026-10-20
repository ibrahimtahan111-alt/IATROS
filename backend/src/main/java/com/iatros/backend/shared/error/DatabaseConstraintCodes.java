package com.iatros.backend.shared.error;

import java.util.Map;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Maps database constraints that represent expected conflicts to API error codes. Services check these rules
 * first; the constraints are the final guarantee under concurrency (decisions.md D-005).
 */
public final class DatabaseConstraintCodes {

	private static final Map<String, String> CONFLICT_CODES = Map.of(
			"appointment_no_overlap", "APPOINTMENT_CONFLICT",
			"app_user_email_uk", "EMAIL_ALREADY_USED",
			"doctor_schedule_period_no_overlap", "SCHEDULE_PERIOD_OVERLAP",
			"patient_doctor_one_primary_uk", "PRIMARY_DOCTOR_ALREADY_SET",
			"patient_doctor_uk", "DOCTOR_ALREADY_AUTHORIZED",
			"consultation_appointment_uk", "CONSULTATION_ALREADY_EXISTS",
			"doctor_user_uk", "DOCTOR_PROFILE_ALREADY_EXISTS");

	private DatabaseConstraintCodes() {
	}

	static Optional<String> conflictCodeFor(String constraintName) {
		return Optional.ofNullable(constraintName).map(CONFLICT_CODES::get);
	}

	/**
	 * Finds the violated constraint name in the exception chain. Hibernate does not extract names for
	 * exclusion constraints, so the PostgreSQL server error (structured, not the localized message) is checked too.
	 */
	public static Optional<String> constraintName(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
				return Optional.of(violation.getConstraintName());
			}
			if (cause instanceof PSQLException psql) {
				ServerErrorMessage serverError = psql.getServerErrorMessage();
				if (serverError != null && serverError.getConstraint() != null) {
					return Optional.of(serverError.getConstraint());
				}
			}
		}
		return Optional.empty();
	}

}
