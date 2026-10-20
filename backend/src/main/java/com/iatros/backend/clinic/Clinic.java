package com.iatros.backend.clinic;

import com.iatros.backend.shared.persistence.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "clinic")
public class Clinic extends AuditedEntity {

	@Column(nullable = false, length = 200)
	private String name;

	@Column(columnDefinition = "citext")
	private String email;

	@Column(length = 40)
	private String phone;

	@Column(length = 500)
	private String address;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ClinicStatus status = ClinicStatus.ACTIVE;

	protected Clinic() {
	}

	public Clinic(String name) {
		this.name = name;
	}

	public void updateDetails(String name, String email, String phone, String address) {
		this.name = name;
		this.email = email;
		this.phone = phone;
		this.address = address;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getPhone() {
		return phone;
	}

	public String getAddress() {
		return address;
	}

	public ClinicStatus getStatus() {
		return status;
	}

}
