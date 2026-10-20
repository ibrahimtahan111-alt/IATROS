package com.iatros.backend.patient.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.iatros.backend.patient.Gender;
import com.iatros.backend.patient.PatientStatus;

/**
 * A patient list row. Only booking-level data (decisions.md D-029), so it is the same for every role. The next
 * appointment and last visit come from the appointments the caller may see (a doctor's own, D-038).
 */
public record PatientSummaryResponse(UUID id, String firstName, String lastName, LocalDate dateOfBirth,
		Gender gender, String phone, String email, PatientStatus status, DoctorRef primaryDoctor,
		Instant nextAppointmentAt, Instant lastVisitAt) {
}
