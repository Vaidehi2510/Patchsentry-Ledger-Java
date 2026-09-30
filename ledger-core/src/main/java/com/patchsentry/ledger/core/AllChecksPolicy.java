package com.patchsentry.ledger.core;

import java.util.List;

/** Stricter policy: optional checks count too, so an optional failure fails the run. */
public final class AllChecksPolicy extends AbstractScoringPolicy {

    public static final String NAME = "all-checks";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected List<CheckResult> inScope(List<CheckResult> checks) {
        return checks;
    }
}
