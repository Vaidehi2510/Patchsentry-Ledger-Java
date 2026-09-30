package com.patchsentry.ledger.core;

import java.util.List;

/**
 * A QA run uploaded by a Patchsentry runner. The {@code recordId} is a stable
 * SHA-256 key for one logical run, so uploads are idempotent: sending the same
 * record twice updates it instead of creating a duplicate.
 */
public record RunReport(
        int schemaVersion,
        String recordId,
        String repository,
        int prNumber,
        String prTitle,
        String revision,
        String phase,
        List<CheckResult> checks,
        List<Finding> findings,
        List<String> limitations,
        AiUsage ai) {

    public RunReport {
        // Defensive, immutable copies: a report is shared across layers and threads.
        checks = checks == null ? List.of() : List.copyOf(checks);
        findings = findings == null ? List.of() : List.copyOf(findings);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
