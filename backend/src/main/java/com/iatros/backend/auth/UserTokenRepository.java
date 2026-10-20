package com.iatros.backend.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

	Optional<UserToken> findByTokenHashAndPurpose(byte[] tokenHash, TokenPurpose purpose);

	List<UserToken> findAllByUserIdAndPurposeAndUsedAtIsNull(UUID userId, TokenPurpose purpose);

}
