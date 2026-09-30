package com.patchsentry.ledger.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A product repository whose pull requests are QA-tested. */
@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 201)
    private String repository;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectEntity() {
        // For JPA.
    }

    public ProjectEntity(UUID id, String repository, String name, Instant createdAt) {
        this.id = id;
        this.repository = repository;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getRepository() {
        return repository;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
