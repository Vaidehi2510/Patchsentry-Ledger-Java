package com.patchsentry.ledger.core;

import java.math.BigDecimal;
import java.util.List;

final class Fixtures {

    static final String RECORD_ID = "a".repeat(64);
    static final String REVISION = "b".repeat(40);

    private Fixtures() {
    }

    static CheckResult check(String id, CheckMethod method, boolean required, CheckStatus status) {
        return new CheckResult(id, "Check " + id, method, required, status, null);
    }

    static CheckResult automated(String id, CheckStatus status) {
        return check(id, CheckMethod.AUTOMATED, true, status);
    }

    static RunReport report(List<CheckResult> checks) {
        return new RunReport(1, RECORD_ID, "acme/shop", 42, "Add express checkout", REVISION, "completed",
                checks,
                List.of(new Finding("Button overflows on mobile", Severity.MEDIUM, "Checkout.jsx",
                        "The pay button leaves the 390px viewport.", "Wrap the button row.")),
                List.of("Synthetic fixture"),
                new AiUsage("local", "qa-local", BigDecimal.ZERO, 3));
    }
}
