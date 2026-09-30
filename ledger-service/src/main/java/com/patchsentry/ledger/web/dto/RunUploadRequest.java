package com.patchsentry.ledger.web.dto;

import com.patchsentry.ledger.core.AiUsage;
import com.patchsentry.ledger.core.CheckResult;
import com.patchsentry.ledger.core.Finding;
import com.patchsentry.ledger.core.RunReport;
import java.util.List;

/**
 * Wire format sent by a runner, modeled on Patchsentry's run-report contract
 * (nested {@code pr} object; lowercase enum values; unknown fields ignored).
 * Mapped to the framework-free {@link RunReport} before validation.
 */
public record RunUploadRequest(
        Integer schemaVersion,
        String recordId,
        String repository,
        PullRequest pr,
        String revision,
        String phase,
        List<CheckResult> checks,
        List<Finding> findings,
        List<String> limitations,
        AiUsage ai) {

    public record PullRequest(Integer number, String title) {
    }

    public RunReport toReport() {
        int number = pr == null || pr.number() == null ? 0 : pr.number();
        String title = pr == null ? null : pr.title();
        return new RunReport(schemaVersion == null ? 0 : schemaVersion, recordId, repository, number, title,
                revision, phase, checks, findings, limitations, ai);
    }
}
