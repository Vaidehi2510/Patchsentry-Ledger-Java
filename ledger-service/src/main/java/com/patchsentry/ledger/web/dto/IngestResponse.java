package com.patchsentry.ledger.web.dto;

import com.patchsentry.ledger.core.Outcome;
import com.patchsentry.ledger.service.IngestResult;

public record IngestResponse(
        String recordId,
        boolean created,
        int score,
        int checksPassed,
        int checksTotal,
        Outcome outcome,
        String scoringPolicy) {

    public static IngestResponse from(String recordId, IngestResult result) {
        return new IngestResponse(recordId, result.created(), result.summary().score(), result.summary().passed(),
                result.summary().total(), result.summary().outcome(), result.scoringPolicy());
    }
}
