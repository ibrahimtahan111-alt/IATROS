package com.iatros.backend.doctor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

	Optional<Doctor> findByIdAndClinicId(UUID id, UUID clinicId);

	Optional<Doctor> findByUserId(UUID userId);

	List<Doctor> findAllByUserIdIn(Collection<UUID> userIds);

	List<Doctor> findAllByClinicId(UUID clinicId);

	/**
	 * Compare-and-set on the schedule version: returns 0 when another save got there first. The row lock it takes
	 * also serializes concurrent schedule saves for the same doctor.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Doctor d set d.scheduleVersion = d.scheduleVersion + 1
			where d.id = :doctorId and d.scheduleVersion = :expectedVersion
			""")
	int advanceScheduleVersion(@Param("doctorId") UUID doctorId, @Param("expectedVersion") long expectedVersion);

}
