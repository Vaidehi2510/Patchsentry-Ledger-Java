package com.patchsentry.ledger.web.dto;

import com.patchsentry.ledger.core.AiUsage;
import com.patchsentry.ledger.core.CheckResult;
import com.patchsentry.ledger.core.Finding;
import com.patchsentry.ledger.evidence.RunEvidenceDocument;
import com.patchsentry.ledger.persistence.CheckResultEntity;
import com.patchsentry.ledger.persistence.QaRunEntity;
import java.util.List;

/** Run summary and checks from Postgres, joined with evidence from MongoDB. */
public record RunDetailResponse(
        RunSummaryResponse summary,
        List<CheckResult> checks,
        boolean evidenceAvailable,
        List<Finding> findings,
        List<String> limitations,
        AiUsage ai) {

    public static RunDetailResponse from(QaRunEntity run, RunEvidenceDocument evidence) {
        List<CheckResult> checks = run.getChecks().stream().map(CheckResultEntity::toCheckResult).toList();
        if (evidence == null) {
            return new RunDetailResponse(RunSummaryResponse.from(run), checks, false, List.of(), List.of(), null);
        }
        return new RunDetailResponse(RunSummaryResponse.from(run), checks, true,
                evidence.findings() == null ? List.of() : evidence.findings(),
                evidence.limitations() == null ? List.of() : evidence.limitations(),
                evidence.ai());
    }
}
