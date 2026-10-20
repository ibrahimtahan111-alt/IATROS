package com.iatros.backend.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Schedule rows carry no clinic_id; callers must first resolve the doctor within the caller's clinic. */
public interface ScheduleExceptionRepository extends JpaRepository<ScheduleException, UUID> {

	List<ScheduleException> findAllByDoctorIdAndExceptionDateBetweenOrderByExceptionDateAscStartTimeAsc(UUID doctorId,
			LocalDate from, LocalDate to);

	List<ScheduleException> findAllByDoctorIdInAndExceptionDateBetween(List<UUID> doctorIds, LocalDate from,
			LocalDate to);

	Optional<ScheduleException> findByIdAndDoctorId(UUID id, UUID doctorId);

}
