package com.iatros.backend.appointment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.appointment.dto.NameRef;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.patient.PatientRepository;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;

/** Names of the patients, doctors, and users shown on appointments, each resolved in batched queries. */
@Component
@Transactional(readOnly = true)
public class AppointmentDirectory {

	private final PatientRepository patients;
	private final DoctorRepository doctors;
	private final AppUserRepository users;

	AppointmentDirectory(PatientRepository patients, DoctorRepository doctors, AppUserRepository users) {
		this.patients = patients;
		this.doctors = doctors;
		this.users = users;
	}

	public Map<UUID, NameRef> patients(Collection<UUID> patientIds) {
		if (patientIds.isEmpty()) {
			return Map.of();
		}
		return patients.findAllById(patientIds)
			.stream()
			.collect(Collectors.toMap(patient -> patient.getId(),
					patient -> new NameRef(patient.getId(), patient.getFirstName(), patient.getLastName())));
	}

	public Map<UUID, NameRef> doctors(Collection<UUID> doctorIds) {
		if (doctorIds.isEmpty()) {
			return Map.of();
		}
		return doctorRefs(doctors.findAllById(doctorIds));
	}

	public Map<UUID, NameRef> doctorRefs(Collection<Doctor> found) {
		Map<UUID, AppUser> usersById = usersById(found.stream().map(Doctor::getUserId).toList());
		return found.stream().collect(Collectors.toMap(Doctor::getId, doctor -> {
			AppUser user = usersById.get(doctor.getUserId());
			return new NameRef(doctor.getId(), user.getFirstName(), user.getLastName());
		}));
	}

	public Map<UUID, AppUser> usersById(Collection<UUID> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return users.findAllById(userIds).stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
	}

	public NameRef user(UUID userId) {
		AppUser user = usersById(List.of(userId)).get(userId);
		return user == null ? null : new NameRef(user.getId(), user.getFirstName(), user.getLastName());
	}

}
