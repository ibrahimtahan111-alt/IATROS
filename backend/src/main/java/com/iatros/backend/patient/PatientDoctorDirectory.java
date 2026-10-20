package com.iatros.backend.patient;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.patient.dto.DoctorRef;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;

/** Resolves the doctors linked to patients into named references, in three batched queries. */
@Component
@Transactional(readOnly = true)
class PatientDoctorDirectory {

	private static final Comparator<DoctorRef> PRIMARY_FIRST_THEN_NAME = Comparator
		.comparing((DoctorRef ref) -> ref.relationshipType() != DoctorRelationshipType.PRIMARY)
		.thenComparing(DoctorRef::lastName, String.CASE_INSENSITIVE_ORDER)
		.thenComparing(DoctorRef::firstName, String.CASE_INSENSITIVE_ORDER);

	private final PatientDoctorRepository patientDoctors;
	private final DoctorRepository doctors;
	private final AppUserRepository users;

	PatientDoctorDirectory(PatientDoctorRepository patientDoctors, DoctorRepository doctors, AppUserRepository users) {
		this.patientDoctors = patientDoctors;
		this.doctors = doctors;
		this.users = users;
	}

	/** Every doctor of each patient, primary first. Patients without links map to an empty list. */
	Map<UUID, List<DoctorRef>> doctorsOf(Collection<UUID> patientIds) {
		if (patientIds.isEmpty()) {
			return Map.of();
		}
		List<PatientDoctor> links = patientDoctors.findAllByPatientIdIn(patientIds);
		Map<UUID, Doctor> doctorsById = doctors.findAllById(links.stream().map(PatientDoctor::getDoctorId).toList())
			.stream()
			.collect(Collectors.toMap(Doctor::getId, Function.identity()));
		Map<UUID, AppUser> usersById = users.findAllById(doctorsById.values().stream().map(Doctor::getUserId).toList())
			.stream()
			.collect(Collectors.toMap(AppUser::getId, Function.identity()));
		return links.stream()
			.collect(Collectors.groupingBy(PatientDoctor::getPatientId, Collectors.mapping(link -> {
				AppUser user = usersById.get(doctorsById.get(link.getDoctorId()).getUserId());
				return new DoctorRef(link.getDoctorId(), user.getFirstName(), user.getLastName(), user.getStatus(),
						link.getRelationshipType());
			}, Collectors.collectingAndThen(Collectors.toList(),
					refs -> refs.stream().sorted(PRIMARY_FIRST_THEN_NAME).toList()))));
	}

	List<DoctorRef> doctorsOf(UUID patientId) {
		return doctorsOf(List.of(patientId)).getOrDefault(patientId, List.of());
	}

	static DoctorRef primaryOf(List<DoctorRef> doctors) {
		return doctors.stream()
			.filter(ref -> ref.relationshipType() == DoctorRelationshipType.PRIMARY)
			.findFirst()
			.orElse(null);
	}

}
