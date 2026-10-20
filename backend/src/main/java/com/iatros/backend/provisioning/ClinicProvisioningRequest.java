package com.iatros.backend.provisioning;

/**
 * Operator input for creating a clinic and its owner (decisions.md D-020). {@code ownerSpecialty} is optional: set it
 * when the owner also practises (a solo doctor, D-033) to create their doctor profile.
 */
public record ClinicProvisioningRequest(String clinicName, String timeZone, String language, String ownerFirstName,
		String ownerLastName, String ownerEmail, String ownerSpecialty) {

	@Override
	public String toString() {
		return "ClinicProvisioningRequest[clinicName=" + clinicName + ", timeZone=" + timeZone + "]";
	}

}
