package com.patchsentry.ledger.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    boolean existsByRepositoryIgnoreCase(String repository);
}
