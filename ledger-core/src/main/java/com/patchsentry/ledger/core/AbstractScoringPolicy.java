package com.patchsentry.ledger.core;

import java.util.List;

/**
 * Template method: subclasses decide which checks are in scope; the scoring and
 * outcome rules are shared so every policy stays truthful about failures.
 */
abstract class AbstractScoringPolicy implements ScoringPolicy {

    /** Checks that count toward the score and the outcome. */
    protected abstract List<CheckResult> inScope(List<CheckResult> checks);

    @Override
    public final RunSummary summarize(List<CheckResult> checks) {
        List<CheckResult> scoped = inScope(checks == null ? List.of() : checks);
        int total = scoped.size();
        int passed = (int) scoped.stream().filter(CheckResult::passed).count();
        // Floor, so 99.9% never displays as 100.
        int score = total == 0 ? 0 : (int) Math.floorDiv(100L * passed, total);
        return new RunSummary(total, passed, score, outcome(scoped));
    }

    private static Outcome outcome(List<CheckResult> scoped) {
        if (scoped.isEmpty()) {
            return Outcome.BLOCKED; // Nothing verified is never a pass.
        }
        if (scoped.stream().anyMatch(c -> c.status() == CheckStatus.EXECUTION_ERROR)) {
            return Outcome.EXECUTION_ERROR;
        }
        if (scoped.stream().anyMatch(c -> c.status() == CheckStatus.FAILED)) {
            return Outcome.FAILED;
        }
        List<CheckResult> pending = scoped.stream().filter(c -> !c.passed()).toList();
        if (pending.isEmpty()) {
            return Outcome.PASSED;
        }
        boolean onlyHumanPending = pending.stream().allMatch(c -> c.method() == CheckMethod.HUMAN);
        return onlyHumanPending ? Outcome.AWAITING_HUMAN : Outcome.BLOCKED;
    }
}
