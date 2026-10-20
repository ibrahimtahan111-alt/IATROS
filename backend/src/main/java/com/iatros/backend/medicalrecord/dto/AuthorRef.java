package com.iatros.backend.medicalrecord.dto;

import java.util.UUID;

/** The staff member who wrote an entry. {@code id} is the user id. */
public record AuthorRef(UUID id, String firstName, String lastName) {
}
