package com.patchsentry.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.patchsentry.ledger.core.CheckMethod;
import com.patchsentry.ledger.core.CheckResult;
import com.patchsentry.ledger.core.CheckStatus;
import com.patchsentry.ledger.core.Outcome;
import com.patchsentry.ledger.core.RequiredChecksPolicy;
import com.patchsentry.ledger.core.RunReport;
import com.patchsentry.ledger.core.RunReportValidator;
import com.patchsentry.ledger.core.ValidationException;
import com.patchsentry.ledger.evidence.RunEvidenceDocument;
import com.patchsentry.ledger.evidence.RunEvidenceRepository;
import com.patchsentry.ledger.persistence.ProjectEntity;
import com.patchsentry.ledger.persistence.ProjectRepository;
import com.patchsentry.ledger.persistence.QaRunEntity;
import com.patchsentry.ledger.persistence.QaRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class RunIngestServiceTest {

    private static final String RECORD = "c".repeat(64);
    private static final String REVISION = "d".repeat(40);

    @Mock
    private ProjectRepository projects;

    @Mock
    private QaRunRepository runs;

    @Mock
    private RunEvidenceRepository evidence;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);
    private final UUID projectId = UUID.randomUUID();
    private RunIngestService service;

    @BeforeEach
    void setUp() {
        service = new RunIngestService(projects, runs, evidence, new RunReportValidator(),
                new RequiredChecksPolicy(), clock);
    }

    private static RunReport report(String repository, String revision, CheckStatus e2eStatus) {
        return new RunReport(1, RECORD, repository, 7, "Express checkout", revision, "completed",
                List.of(new CheckResult("unit", "Unit tests", CheckMethod.AUTOMATED, true, CheckStatus.PASSED, null),
                        new CheckResult("e2e", "Checkout journey", CheckMethod.AUTOMATED, true, e2eStatus, null)),
                List.of(), List.of(), null);
    }

    private void projectExists() {
        when(projects.findById(projectId))
                .thenReturn(Optional.of(new ProjectEntity(projectId, "acme/shop", "Shop", clock.instant())));
    }

    @Test
    void firstUploadCreatesRunChecksAndEvidence() {
        projectExists();
        when(runs.findById(RECORD)).thenReturn(Optional.empty());

        IngestResult result = service.ingest(projectId, RECORD, report("acme/shop", REVISION, CheckStatus.FAILED));

        assertThat(result.created()).isTrue();
        assertThat(result.summary().score()).isEqualTo(50);
        assertThat(result.summary().outcome()).isEqualTo(Outcome.FAILED);
        ArgumentCaptor<QaRunEntity> saved = ArgumentCaptor.forClass(QaRunEntity.class);
        verify(runs).save(saved.capture());
        assertThat(saved.getValue().getChecks()).hasSize(2);
        assertThat(saved.getValue().getUploadCount()).isEqualTo(1);
        verify(evidence).save(any(RunEvidenceDocument.class));
    }

    @Test
    void reuploadUpdatesTheSameRunInsteadOfDuplicatingIt() {
        projectExists();
        QaRunEntity existing = new QaRunEntity(RECORD, projectId, 7, REVISION, clock.instant());
        existing.apply(report("acme/shop", REVISION, CheckStatus.FAILED),
                new RequiredChecksPolicy().summarize(report("acme/shop", REVISION, CheckStatus.FAILED).checks()),
                RequiredChecksPolicy.NAME, clock.instant());
        when(runs.findById(RECORD)).thenReturn(Optional.of(existing));

        IngestResult result = service.ingest(projectId, RECORD, report("acme/shop", REVISION, CheckStatus.PASSED));

        assertThat(result.created()).isFalse();
        assertThat(result.summary().outcome()).isEqualTo(Outcome.PASSED);
        assertThat(existing.getChecks()).hasSize(2);
        assertThat(existing.getUploadCount()).isEqualTo(2);
        assertThat(existing.getScore()).isEqualTo(100);
    }

    @Test
    void recordIdCannotMoveToAnotherRevision() {
        projectExists();
        when(runs.findById(RECORD)).thenReturn(Optional.of(new QaRunEntity(RECORD, projectId, 7, REVISION, clock.instant())));

        assertThatThrownBy(() -> service.ingest(projectId, RECORD, report("acme/shop", "e".repeat(40), CheckStatus.PASSED)))
                .isInstanceOf(ConflictException.class);
        verify(runs, never()).save(any());
        verifyNoInteractions(evidence);
    }

    @Test
    void tokenForOneRepositoryCannotUploadForAnother() {
        projectExists();

        assertThatThrownBy(() -> service.ingest(projectId, RECORD, report("acme/other", REVISION, CheckStatus.PASSED)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("acme/shop");
        verifyNoInteractions(runs, evidence);
    }

    @Test
    void urlAndBodyRecordIdsMustMatch() {
        assertThatThrownBy(() -> service.ingest(projectId, "f".repeat(64), report("acme/shop", REVISION, CheckStatus.PASSED)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(projects, runs, evidence);
    }

    @Test
    void invalidReportsNeverTouchStorage() {
        RunReport invalid = new RunReport(1, RECORD, "acme/shop", 7, "t", "short", "completed",
                List.of(), List.of(), List.of(), null);
        assertThatThrownBy(() -> service.ingest(projectId, RECORD, invalid))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(projects, runs, evidence);
    }

    @Test
    void evidenceStoreFailurePropagatesSoPostgresRollsBack() {
        projectExists();
        when(runs.findById(RECORD)).thenReturn(Optional.empty());
        when(evidence.save(any(RunEvidenceDocument.class))).thenThrow(new DataAccessResourceFailureException("mongo down"));

        assertThatThrownBy(() -> service.ingest(projectId, RECORD, report("acme/shop", REVISION, CheckStatus.PASSED)))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
