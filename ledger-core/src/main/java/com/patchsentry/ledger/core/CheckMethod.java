package com.patchsentry.ledger.core;

/** How a check is satisfied: by a test run, a person, or an AI/static analysis. */
public enum CheckMethod {
    AUTOMATED, HUMAN, ANALYSIS
}
