package com.patchsentry.ledger.service;

import com.patchsentry.ledger.core.RunSummary;

public record IngestResult(boolean created, RunSummary summary, String scoringPolicy) {
}
