package com.patchsentry.ledger.persistence;

import com.patchsentry.ledger.core.Outcome;
import com.patchsentry.ledger.core.RunReport;
import com.patchsentry.ledger.core.RunSummary;
import com.patchsentry.ledger.service.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One logical QA run. The record ID is the idempotency key; it stays bound to the
 * project, pull request, and revision it was first uploaded for. {@code @Version}
 * gives optimistic locking when two replicas update the same run at once.
 */
@Entity
@Table(name = "qa_runs")
public class QaRunEntity {

    @Id
    @Column(name = "record_id", length = 64)
    private String recordId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "pr_title", length = 300)
    private String prTitle;

    @Column(nullable = false, length = 40)
    private String revision;

    @Column(length = 40)
    private String phase;

    @Column(nullable = false)
    private int score;

    @Column(name = "checks_total", nullable = false)
    private int checksTotal;

    @Column(name = "checks_passed", nullable = false)
    private int checksPassed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    @Column(name = "scoring_policy", nullable = false, length = 40)
    private String scoringPolicy;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "upload_count", nullable = false)
    private int uploadCount;

    @Version
    private Long version;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<CheckResultEntity> checks = new ArrayList<>();

    protected QaRunEntity() {
        // For JPA.
    }

    public QaRunEntity(String recordId, UUID projectId, int prNumber, String revision, Instant receivedAt) {
        this.recordId = recordId;
        this.projectId = projectId;
        this.prNumber = prNumber;
        this.revision = revision;
        this.receivedAt = receivedAt;
        this.updatedAt = receivedAt;
    }

    /** A record ID may be re-uploaded, but never moved to another project, PR, or commit. */
    public void assertSameIdentity(UUID otherProjectId, int otherPrNumber, String otherRevision) {
        if (!projectId.equals(otherProjectId) || prNumber != otherPrNumber || !revision.equals(otherRevision)) {
            throw new ConflictException(
                    "Record " + recordId + " is already bound to a different project, pull request, or revision");
        }
    }

    /** Replaces the run's results with the latest upload. */
    public void apply(RunReport report, RunSummary summary, String policyName, Instant now) {
        this.prTitle = report.prTitle();
        this.phase = report.phase();
        this.score = summary.score();
        this.checksTotal = summary.total();
        this.checksPassed = summary.passed();
        this.outcome = summary.outcome();
        this.scoringPolicy = policyName;
        this.updatedAt = now;
        this.uploadCount++;
        // orphanRemoval deletes the old rows on flush. No unique constraint on
        // (run, check_id) because Hibernate flushes inserts before deletes.
        this.checks.clear();
        for (int i = 0; i < report.checks().size(); i++) {
            this.checks.add(new CheckResultEntity(this, i, report.checks().get(i)));
        }
    }

    public String getRecordId() {
        return recordId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getPrNumber() {
        return prNumber;
    }

    public String getPrTitle() {
        return prTitle;
    }

    public String getRevision() {
        return revision;
    }

    public String getPhase() {
        return phase;
    }

    public int getScore() {
        return score;
    }

    public int getChecksTotal() {
        return checksTotal;
    }

    public int getChecksPassed() {
        return checksPassed;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getScoringPolicy() {
        return scoringPolicy;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getUploadCount() {
        return uploadCount;
    }

    public List<CheckResultEntity> getChecks() {
        return Collections.unmodifiableList(checks);
    }
}
