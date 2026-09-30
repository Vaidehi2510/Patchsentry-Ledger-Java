package com.patchsentry.ledger.web.dto;

import com.patchsentry.ledger.core.Outcome;
import com.patchsentry.ledger.persistence.QaRunEntity;
import java.time.Instant;

public record RunSummaryResponse(
        String recordId,
        int prNumber,
        String prTitle,
        String revision,
        String phase,
        int score,
        int checksPassed,
        int checksTotal,
        Outcome outcome,
        String scoringPolicy,
        int uploadCount,
        Instant receivedAt,
        Instant updatedAt) {

    public static RunSummaryResponse from(QaRunEntity run) {
        return new RunSummaryResponse(run.getRecordId(), run.getPrNumber(), run.getPrTitle(), run.getRevision(),
                run.getPhase(), run.getScore(), run.getChecksPassed(), run.getChecksTotal(), run.getOutcome(),
                run.getScoringPolicy(), run.getUploadCount(), run.getReceivedAt(), run.getUpdatedAt());
    }
}
