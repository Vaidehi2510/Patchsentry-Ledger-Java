package com.patchsentry.ledger.core;

import java.util.List;

/** Default policy: only required checks decide the score and outcome. */
public final class RequiredChecksPolicy extends AbstractScoringPolicy {

    public static final String NAME = "required-checks";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected List<CheckResult> inScope(List<CheckResult> checks) {
        return checks.stream().filter(CheckResult::required).toList();
    }
}
