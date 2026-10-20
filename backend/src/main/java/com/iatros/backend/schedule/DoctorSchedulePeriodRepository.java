package com.iatros.backend.schedule;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Schedule rows carry no clinic_id; callers must first resolve the doctor within the caller's clinic. */
public interface DoctorSchedulePeriodRepository extends JpaRepository<DoctorSchedulePeriod, UUID> {

	List<DoctorSchedulePeriod> findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(UUID doctorId);

	List<DoctorSchedulePeriod> findAllByDoctorIdIn(List<UUID> doctorIds);

	/**
	 * Runs immediately, unlike a derived delete: Hibernate flushes inserts before deletes, so replacing a schedule
	 * through the persistence context would trip the no-overlap constraint.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from DoctorSchedulePeriod p where p.doctorId = :doctorId")
	void deleteAllForDoctor(@Param("doctorId") UUID doctorId);

}
