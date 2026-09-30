package com.patchsentry.ledger.service;

import com.patchsentry.ledger.core.RunReport;
import com.patchsentry.ledger.core.RunReportValidator;
import com.patchsentry.ledger.core.RunSummary;
import com.patchsentry.ledger.core.ScoringPolicy;
import com.patchsentry.ledger.core.ValidationException;
import com.patchsentry.ledger.evidence.RunEvidenceDocument;
import com.patchsentry.ledger.evidence.RunEvidenceRepository;
import com.patchsentry.ledger.persistence.ProjectEntity;
import com.patchsentry.ledger.persistence.ProjectRepository;
import com.patchsentry.ledger.persistence.QaRunEntity;
import com.patchsentry.ledger.persistence.QaRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent upsert of a runner's report.
 *
 * <p>Consistency across the two stores: evidence is written to MongoDB inside the
 * Postgres transaction, before commit. If MongoDB fails, the exception rolls the
 * Postgres changes back. If the commit itself fails afterwards, the evidence
 * document is orphaned but harmless: it is keyed by record ID and overwritten when
 * the runner retries.
 */
@Service
public class RunIngestService {

    private final ProjectRepository projects;
    private final QaRunRepository runs;
    private final RunEvidenceRepository evidence;
    private final RunReportValidator validator;
    private final ScoringPolicy scoringPolicy;
    private final Clock clock;

    public RunIngestService(ProjectRepository projects, QaRunRepository runs, RunEvidenceRepository evidence,
            RunReportValidator validator, ScoringPolicy scoringPolicy, Clock clock) {
        this.projects = projects;
        this.runs = runs;
        this.evidence = evidence;
        this.validator = validator;
        this.scoringPolicy = scoringPolicy;
        this.clock = clock;
    }

    @Transactional
    public IngestResult ingest(UUID projectId, String pathRecordId, RunReport report) {
        if (report == null || !pathRecordId.equals(report.recordId())) {
            throw new ValidationException(List.of("recordId in the URL and the body must match"));
        }
        validator.validate(report);

        ProjectEntity project = projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project " + projectId + " not found"));
        if (!project.getRepository().equalsIgnoreCase(report.repository())) {
            throw new ConflictException("This runner token belongs to " + project.getRepository()
                    + ", not " + report.repository());
        }

        RunSummary summary = scoringPolicy.summarize(report.checks());
        Instant now = clock.instant();
        Optional<QaRunEntity> existing = runs.findById(report.recordId());
        existing.ifPresent(run -> run.assertSameIdentity(projectId, report.prNumber(), report.revision()));
        QaRunEntity run = existing.orElseGet(
                () -> new QaRunEntity(report.recordId(), projectId, report.prNumber(), report.revision(), now));

        run.apply(report, summary, scoringPolicy.name(), now);
        runs.save(run);
        evidence.save(RunEvidenceDocument.from(report, projectId, now));
        return new IngestResult(existing.isEmpty(), summary, scoringPolicy.name());
    }
}
