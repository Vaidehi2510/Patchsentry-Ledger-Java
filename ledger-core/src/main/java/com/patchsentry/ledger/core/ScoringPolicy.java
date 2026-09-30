package com.patchsentry.ledger.core;

import java.util.List;

/**
 * Strategy for turning check results into a score and an outcome. The service
 * selects one implementation from configuration (see {@link ScoringPolicies}).
 */
public interface ScoringPolicy {

    String name();

    RunSummary summarize(List<CheckResult> checks);
}
