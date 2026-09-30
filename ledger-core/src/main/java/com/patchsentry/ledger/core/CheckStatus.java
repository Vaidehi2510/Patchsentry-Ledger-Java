package com.patchsentry.ledger.core;

/** Result of a single QA check. Only {@link #PASSED} counts toward the score. */
public enum CheckStatus {
    PASSED, FAILED, BLOCKED, SKIPPED, EXECUTION_ERROR
}
