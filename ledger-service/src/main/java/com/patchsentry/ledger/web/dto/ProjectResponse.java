package com.patchsentry.ledger.web.dto;

import com.patchsentry.ledger.persistence.ProjectEntity;
import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(UUID id, String repository, String name, Instant createdAt) {

    public static ProjectResponse from(ProjectEntity project) {
        return new ProjectResponse(project.getId(), project.getRepository(), project.getName(), project.getCreatedAt());
    }
}
