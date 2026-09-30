package com.patchsentry.ledger.core;

import static com.patchsentry.ledger.core.Fixtures.automated;
import static com.patchsentry.ledger.core.Fixtures.report;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunReportValidatorTest {

    private final RunReportValidator validator = new RunReportValidator();

    @Test
    void acceptsAWellFormedReport() {
        assertDoesNotThrow(() -> validator.validate(report(List.of(automated("unit", CheckStatus.PASSED)))));
    }

    @Test
    void reportsEveryProblemAtOnce() {
        RunReport bad = new RunReport(2, "not-a-hash", "no-slash", 0, "t", "abc", "completed",
                List.of(), null, null, null);
        List<String> problems = validator.problems(bad);
        assertEquals(6, problems.size(), problems.toString());
    }

    @Test
    void rejectsDuplicateCheckIds() {
        ValidationException error = assertThrows(ValidationException.class, () -> validator.validate(
                report(List.of(automated("unit", CheckStatus.PASSED), automated("unit", CheckStatus.FAILED)))));
        assertTrue(error.errors().get(0).contains("duplicated"), error.errors().toString());
    }

    @Test
    void rejectsMissingStatusAndMethod() {
        CheckResult incomplete = new CheckResult("unit", "Unit", null, true, null, null);
        List<String> problems = validator.problems(report(List.of(incomplete)));
        assertEquals(List.of("checks[0].method is required", "checks[0].status is required"), problems);
    }

    @Test
    void boundsPayloadSize() {
        List<CheckResult> many = new ArrayList<>();
        for (int i = 0; i <= RunReportValidator.MAX_CHECKS; i++) {
            many.add(automated("check-" + i, CheckStatus.PASSED));
        }
        assertTrue(validator.problems(report(many)).contains("at most 200 checks are allowed"));

        CheckResult verbose = new CheckResult("unit", "Unit", CheckMethod.AUTOMATED, true, CheckStatus.FAILED,
                "x".repeat(RunReportValidator.MAX_TEXT + 1));
        assertEquals(List.of("checks[0].details exceeds 4000 characters"), validator.problems(report(List.of(verbose))));
    }

    @Test
    void rejectsNegativeCost() {
        RunReport base = report(List.of(automated("unit", CheckStatus.PASSED)));
        RunReport negative = new RunReport(base.schemaVersion(), base.recordId(), base.repository(), base.prNumber(),
                base.prTitle(), base.revision(), base.phase(), base.checks(), base.findings(), base.limitations(),
                new AiUsage("openrouter", "model", new BigDecimal("-0.01"), 1));
        assertEquals(List.of("ai.costUsd cannot be negative"), validator.problems(negative));
    }

    @Test
    void reportListsAreImmutable() {
        RunReport base = report(List.of(automated("unit", CheckStatus.PASSED)));
        assertThrows(UnsupportedOperationException.class, () -> base.checks().add(automated("x", CheckStatus.PASSED)));
    }
}
