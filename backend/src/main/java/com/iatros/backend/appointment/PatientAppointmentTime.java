package com.iatros.backend.appointment;

import java.time.Instant;
import java.util.UUID;

/** A per-patient appointment time from an aggregate query (next appointment, last visit). */
public record PatientAppointmentTime(UUID patientId, Instant startAt) {
}
