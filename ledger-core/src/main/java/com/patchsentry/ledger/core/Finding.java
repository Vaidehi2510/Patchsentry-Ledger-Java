package com.patchsentry.ledger.core;

/** A reviewer or AI finding attached to a run. Stored as evidence, never used for scoring. */
public record Finding(
        String title,
        Severity severity,
        String file,
        String description,
        String suggestedFix) {
}
