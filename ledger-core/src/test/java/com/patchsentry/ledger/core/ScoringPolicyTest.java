package com.patchsentry.ledger.core;

import static com.patchsentry.ledger.core.Fixtures.automated;
import static com.patchsentry.ledger.core.Fixtures.check;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringPolicyTest {

    private final ScoringPolicy required = new RequiredChecksPolicy();
    private final ScoringPolicy all = new AllChecksPolicy();

    @Test
    void allRequiredChecksPassing() {
        RunSummary summary = required.summarize(List.of(
                automated("unit", CheckStatus.PASSED), automated("e2e", CheckStatus.PASSED)));
        assertEquals(new RunSummary(2, 2, 100, Outcome.PASSED), summary);
    }

    @Test
    void scoreIsFlooredSoItNeverRoundsUpToPerfect() {
        RunSummary summary = required.summarize(List.of(
                automated("a", CheckStatus.PASSED), automated("b", CheckStatus.PASSED),
                automated("c", CheckStatus.BLOCKED)));
        assertEquals(66, summary.score());
        assertEquals(Outcome.BLOCKED, summary.outcome());
    }

    @Test
    void failureOutranksBlockedAndErrorOutranksFailure() {
        assertEquals(Outcome.FAILED, required.summarize(List.of(
                automated("a", CheckStatus.BLOCKED), automated("b", CheckStatus.FAILED))).outcome());
        assertEquals(Outcome.EXECUTION_ERROR, required.summarize(List.of(
                automated("a", CheckStatus.FAILED), automated("b", CheckStatus.EXECUTION_ERROR))).outcome());
    }

    @Test
    void onlyHumanChecksPendingMeansAwaitingHuman() {
        RunSummary summary = required.summarize(List.of(
                automated("unit", CheckStatus.PASSED),
                check("owner-question", CheckMethod.HUMAN, true, CheckStatus.BLOCKED)));
        assertEquals(Outcome.AWAITING_HUMAN, summary.outcome());
        assertEquals(50, summary.score());
    }

    @Test
    void noRequiredChecksIsBlockedNotPassed() {
        RunSummary summary = required.summarize(List.of(
                check("lint", CheckMethod.AUTOMATED, false, CheckStatus.PASSED)));
        assertEquals(new RunSummary(0, 0, 0, Outcome.BLOCKED), summary);
    }

    @Test
    void optionalFailureOnlyMattersUnderAllChecksPolicy() {
        List<CheckResult> checks = List.of(
                automated("unit", CheckStatus.PASSED),
                check("visual", CheckMethod.AUTOMATED, false, CheckStatus.FAILED));
        assertEquals(Outcome.PASSED, required.summarize(checks).outcome());
        assertEquals(Outcome.FAILED, all.summarize(checks).outcome());
        assertEquals(50, all.summarize(checks).score());
    }

    @Test
    void factoryResolvesConfiguredNames() {
        assertEquals(RequiredChecksPolicy.NAME, ScoringPolicies.byName("required-checks").name());
        assertEquals(AllChecksPolicy.NAME, ScoringPolicies.byName("all-checks").name());
        assertThrows(IllegalArgumentException.class, () -> ScoringPolicies.byName("lenient"));
    }
}
