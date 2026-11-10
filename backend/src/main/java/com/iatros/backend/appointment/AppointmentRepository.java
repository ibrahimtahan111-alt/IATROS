package com.iatros.backend.appointment;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

	/**
	 * Matches {@code appointment_max_duration}. A row that is still ongoing at {@code instant} started after
	 * {@code instant} minus this duration, so overlap checks can use the {@code start_at} indexes.
	 */
	Duration MAX_DURATION = Duration.ofHours(12);

	Optional<Appointment> findByIdAndClinicId(UUID id, UUID clinicId);

	/** Appointments of the given doctors that intersect [from, to), ordered by start. */
	default List<Appointment> findOverlapping(UUID clinicId, Collection<UUID> doctorIds, Instant from, Instant to) {
		return findOverlappingAfter(clinicId, doctorIds, from.minus(MAX_DURATION), from, to);
	}

	@Query("""
			select a from Appointment a
			where a.clinicId = :clinicId
			  and a.doctorId in :doctorIds
			  and a.startAt > :earliestStart
			  and a.startAt < :to
			  and a.endAt > :from
			order by a.startAt
			""")
	List<Appointment> findOverlappingAfter(@Param("clinicId") UUID clinicId,
			@Param("doctorIds") Collection<UUID> doctorIds, @Param("earliestStart") Instant earliestStart,
			@Param("from") Instant from, @Param("to") Instant to);

	/**
	 * The paginated appointment list (api.md §17). {@code scopeDoctorId} is the caller's data scope (doctors see
	 * their own appointments); the other filters are the user's. Start bounds are always bound (open ends use
	 * sentinels) because PostgreSQL cannot type a null timestamp parameter.
	 */
	@Query("""
			select a from Appointment a
			where a.clinicId = :clinicId
			  and (:scopeDoctorId is null or a.doctorId = :scopeDoctorId)
			  and (:doctorId is null or a.doctorId = :doctorId)
			  and (:patientId is null or a.patientId = :patientId)
			  and a.status in :statuses
			  and a.type in :types
			  and a.startAt >= :from
			  and a.startAt < :to
			""")
	Page<Appointment> search(@Param("clinicId") UUID clinicId, @Param("scopeDoctorId") UUID scopeDoctorId,
			@Param("doctorId") UUID doctorId, @Param("patientId") UUID patientId,
			@Param("statuses") Collection<AppointmentStatus> statuses, @Param("types") Collection<AppointmentType> types,
			@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

	/** Appointments starting in [from, to), of one doctor when {@code doctorId} is given, ordered by start. */
	@Query("""
			select a from Appointment a
			where a.clinicId = :clinicId
			  and (:doctorId is null or a.doctorId = :doctorId)
			  and a.startAt >= :from
			  and a.startAt < :to
			order by a.startAt, a.id
			""")
	List<Appointment> findStarting(@Param("clinicId") UUID clinicId, @Param("doctorId") UUID doctorId,
			@Param("from") Instant from, @Param("to") Instant to);

	/**
	 * The doctor's most recently seen patients (latest COMPLETED appointment first) that the doctor is still
	 * authorized for.
	 */
	@Query("""
			select new com.iatros.backend.appointment.PatientAppointmentTime(a.patientId, max(a.startAt))
			from Appointment a
			where a.doctorId = :doctorId
			  and a.status = com.iatros.backend.appointment.AppointmentStatus.COMPLETED
			  and exists (select 1 from PatientDoctor pd where pd.patientId = a.patientId and pd.doctorId = :doctorId)
			group by a.patientId
			order by max(a.startAt) desc
			""")
	List<PatientAppointmentTime> findRecentlySeen(@Param("doctorId") UUID doctorId, Pageable pageable);

	/** True if a time-blocking appointment of the doctor intersects [startAt, endAt), excluding one appointment. */
	default boolean existsConflict(UUID doctorId, Instant startAt, Instant endAt, UUID excludeId) {
		return existsConflictAfter(doctorId, startAt.minus(MAX_DURATION), startAt, endAt, excludeId);
	}

	@Query("""
			select count(a) > 0 from Appointment a
			where a.doctorId = :doctorId
			  and a.status not in (com.iatros.backend.appointment.AppointmentStatus.CANCELLED,
			                       com.iatros.backend.appointment.AppointmentStatus.NO_SHOW)
			  and a.startAt > :earliestStart
			  and a.startAt < :endAt
			  and a.endAt > :startAt
			  and (:excludeId is null or a.id <> :excludeId)
			""")
	boolean existsConflictAfter(@Param("doctorId") UUID doctorId, @Param("earliestStart") Instant earliestStart,
			@Param("startAt") Instant startAt, @Param("endAt") Instant endAt, @Param("excludeId") UUID excludeId);

	/** True if the doctor has an appointment that has not ended and is still expected to take place. */
	default boolean existsUpcomingForDoctor(UUID doctorId, Instant now) {
		return existsUpcomingForDoctorAfter(doctorId, now.minus(MAX_DURATION), now);
	}

	@Query("""
			select count(a) > 0 from Appointment a
			where a.doctorId = :doctorId
			  and a.startAt > :earliestStart
			  and a.endAt > :now
			  and a.status in (com.iatros.backend.appointment.AppointmentStatus.SCHEDULED,
			                   com.iatros.backend.appointment.AppointmentStatus.CONFIRMED,
			                   com.iatros.backend.appointment.AppointmentStatus.WAITING,
			                   com.iatros.backend.appointment.AppointmentStatus.IN_CONSULTATION)
			""")
	boolean existsUpcomingForDoctorAfter(@Param("doctorId") UUID doctorId,
			@Param("earliestStart") Instant earliestStart, @Param("now") Instant now);

	/** True if the patient has an appointment that has not ended and is still expected to take place. */
	default boolean existsUpcomingForPatient(UUID patientId, Instant now) {
		return existsUpcomingForPatientAfter(patientId, now.minus(MAX_DURATION), now);
	}

	@Query("""
			select count(a) > 0 from Appointment a
			where a.patientId = :patientId
			  and a.startAt > :earliestStart
			  and a.endAt > :now
			  and a.status in (com.iatros.backend.appointment.AppointmentStatus.SCHEDULED,
			                   com.iatros.backend.appointment.AppointmentStatus.CONFIRMED,
			                   com.iatros.backend.appointment.AppointmentStatus.WAITING,
			                   com.iatros.backend.appointment.AppointmentStatus.IN_CONSULTATION)
			""")
	boolean existsUpcomingForPatientAfter(@Param("patientId") UUID patientId,
			@Param("earliestStart") Instant earliestStart, @Param("now") Instant now);

	/** Each patient's earliest appointment starting at or after {@code now} that is still expected to take place. */
	@Query("""
			select new com.iatros.backend.appointment.PatientAppointmentTime(a.patientId, min(a.startAt))
			from Appointment a
			where a.clinicId = :clinicId
			  and a.patientId in :patientIds
			  and (:scopeDoctorId is null or a.doctorId = :scopeDoctorId)
			  and a.startAt >= :now
			  and a.status in (com.iatros.backend.appointment.AppointmentStatus.SCHEDULED,
			                   com.iatros.backend.appointment.AppointmentStatus.CONFIRMED,
			                   com.iatros.backend.appointment.AppointmentStatus.WAITING)
			group by a.patientId
			""")
	List<PatientAppointmentTime> findNextStarts(@Param("clinicId") UUID clinicId,
			@Param("patientIds") Collection<UUID> patientIds, @Param("scopeDoctorId") UUID scopeDoctorId,
			@Param("now") Instant now);

	/** Each patient's latest completed appointment. */
	@Query("""
			select new com.iatros.backend.appointment.PatientAppointmentTime(a.patientId, max(a.startAt))
			from Appointment a
			where a.clinicId = :clinicId
			  and a.patientId in :patientIds
			  and (:scopeDoctorId is null or a.doctorId = :scopeDoctorId)
			  and a.status = com.iatros.backend.appointment.AppointmentStatus.COMPLETED
			group by a.patientId
			""")
	List<PatientAppointmentTime> findLastVisits(@Param("clinicId") UUID clinicId,
			@Param("patientIds") Collection<UUID> patientIds, @Param("scopeDoctorId") UUID scopeDoctorId);

}
