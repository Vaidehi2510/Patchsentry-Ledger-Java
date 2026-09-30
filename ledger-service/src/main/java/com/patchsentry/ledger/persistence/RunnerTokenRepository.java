package com.patchsentry.ledger.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunnerTokenRepository extends JpaRepository<RunnerTokenEntity, UUID> {

    Optional<RunnerTokenEntity> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
