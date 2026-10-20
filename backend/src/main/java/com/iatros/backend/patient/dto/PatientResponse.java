package com.iatros.backend.patient.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.iatros.backend.patient.Gender;
import com.iatros.backend.patient.PatientStatus;

/**
 * A patient's administrative profile (decisions.md D-029, D-036). {@code doctors} (primary first, then authorized)
 * is null for secretaries, who only see the primary doctor. The next appointment and last visit come from the
 * appointments the caller may see (a doctor's own, D-038).
 */
public record PatientResponse(
		UUID id,
		String firstName,
		String lastName,
		LocalDate dateOfBirth,
		Gender gender,
		String phone,
		String email,
		String address,
		String emergencyContactName,
		String emergencyContactPhone,
		PatientStatus status,
		Instant archivedAt,
		Instant createdAt,
		long version,
		DoctorRef primaryDoctor,
		List<DoctorRef> doctors,
		Instant nextAppointmentAt,
		Instant lastVisitAt) {
}
