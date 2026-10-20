package com.iatros.backend.patient.dto;

import java.time.LocalDate;

import com.iatros.backend.patient.Gender;

/** Identity and contact fields shared by create and update (decisions.md D-036). */
public interface PatientDetailsRequest {

	String firstName();

	String lastName();

	LocalDate dateOfBirth();

	Gender gender();

	String phone();

	String email();

	String address();

	String emergencyContactName();

	String emergencyContactPhone();

}
