package com.iatros.backend.consultation.dto;

import java.util.UUID;

/** The doctor who held a consultation. {@code id} is the doctor profile ID. */
public record ConsultationDoctorRef(UUID id, String firstName, String lastName) {
}
