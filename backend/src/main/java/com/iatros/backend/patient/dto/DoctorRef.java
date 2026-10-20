package com.iatros.backend.patient.dto;

import java.util.UUID;

import com.iatros.backend.patient.DoctorRelationshipType;
import com.iatros.backend.user.UserStatus;

/** A doctor linked to a patient, with what the UI needs to show it. */
public record DoctorRef(UUID id, String firstName, String lastName, UserStatus status,
		DoctorRelationshipType relationshipType) {
}
