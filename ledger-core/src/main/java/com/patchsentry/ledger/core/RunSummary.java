package com.patchsentry.ledger.core;

/** Score and outcome derived from a run's checks by a {@link ScoringPolicy}. */
public record RunSummary(int total, int passed, int score, Outcome outcome) {

    public RunSummary {
        if (total < 0 || passed < 0 || passed > total) {
            throw new IllegalArgumentException("passed must be between 0 and total");
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
    }
}
