package com.patchsentry.ledger.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaRunRepository extends JpaRepository<QaRunEntity, String> {

    List<QaRunEntity> findByProjectIdOrderByUpdatedAtDesc(UUID projectId, Pageable page);

    List<QaRunEntity> findByProjectIdAndPrNumberOrderByUpdatedAtDesc(UUID projectId, int prNumber, Pageable page);

    Optional<QaRunEntity> findByRecordIdAndProjectId(String recordId, UUID projectId);
}
