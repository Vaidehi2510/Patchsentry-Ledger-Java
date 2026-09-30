package com.patchsentry.ledger.core;

/** One check in a QA run, as reported by the runner. */
public record CheckResult(
        String id,
        String name,
        CheckMethod method,
        boolean required,
        CheckStatus status,
        String details) {

    public boolean passed() {
        return status == CheckStatus.PASSED;
    }
}
