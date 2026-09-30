package com.patchsentry.ledger.core;

/** Overall QA decision for one revision of a pull request. */
public enum Outcome {
    PASSED, FAILED, BLOCKED, AWAITING_HUMAN, EXECUTION_ERROR
}
