package com.patchsentry.ledger.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A runner credential. Only the SHA-256 hash of the token is stored. */
@Entity
@Table(name = "runner_tokens")
public class RunnerTokenEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "display_prefix", nullable = false, length = 16)
    private String displayPrefix;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RunnerTokenEntity() {
        // For JPA.
    }

    public RunnerTokenEntity(UUID id, UUID projectId, String tokenHash, String displayPrefix, Instant createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.tokenHash = tokenHash;
        this.displayPrefix = displayPrefix;
        this.createdAt = createdAt;
    }

    public void revoke(Instant when) {
        if (revokedAt == null) {
            revokedAt = when;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getDisplayPrefix() {
        return displayPrefix;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
