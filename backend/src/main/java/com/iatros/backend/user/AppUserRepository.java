package com.iatros.backend.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	/**
	 * Case-insensitive lookup. The parameter must be cast to citext: JDBC binds strings as varchar,
	 * and citext = varchar compares as case-sensitive text.
	 */
	@NativeQuery("select * from app_user where email = cast(:email as citext)")
	Optional<AppUser> findByEmail(@Param("email") String email);

	@NativeQuery("select exists (select 1 from app_user where email = cast(:email as citext))")
	boolean existsByEmail(@Param("email") String email);

	Optional<AppUser> findByIdAndClinicId(UUID id, UUID clinicId);

	/**
	 * Clinic members matching optional filters. {@code pattern} is a lower-case LIKE pattern from SearchTerms,
	 * matched against "first last" and the email.
	 */
	@Query("""
			select u from AppUser u
			where u.clinicId = :clinicId
			  and (:role is null or u.role = :role)
			  and (:status is null or u.status = :status)
			  and (:pattern is null
			       or lower(concat(u.firstName, ' ', u.lastName)) like :pattern escape '\\'
			       or lower(cast(u.email as String)) like :pattern escape '\\')
			""")
	Page<AppUser> search(@Param("clinicId") UUID clinicId, @Param("role") Role role,
			@Param("status") UserStatus status, @Param("pattern") String pattern, Pageable pageable);

	/**
	 * Clinic members who have a doctor profile (doctors and practising owners, D-033), with optional filters.
	 * {@code userId} restricts the result to one member (a doctor's own data scope).
	 */
	@Query("""
			select u from AppUser u
			where u.clinicId = :clinicId
			  and exists (select 1 from Doctor d
			              where d.userId = u.id and d.clinicId = :clinicId
			                and (:specialtyId is null or d.primarySpecialtyId = :specialtyId))
			  and (:userId is null or u.id = :userId)
			  and (:status is null or u.status = :status)
			  and (:pattern is null
			       or lower(concat(u.firstName, ' ', u.lastName)) like :pattern escape '\\'
			       or lower(cast(u.email as String)) like :pattern escape '\\')
			""")
	Page<AppUser> searchDoctors(@Param("clinicId") UUID clinicId, @Param("userId") UUID userId,
			@Param("specialtyId") UUID specialtyId, @Param("status") UserStatus status,
			@Param("pattern") String pattern, Pageable pageable);

}
