package com.patchsentry.ledger.core;

import java.util.Map;
import java.util.function.Supplier;

/** Factory that maps a configured policy name to its strategy. */
public final class ScoringPolicies {

    private static final Map<String, Supplier<ScoringPolicy>> POLICIES = Map.of(
            RequiredChecksPolicy.NAME, RequiredChecksPolicy::new,
            AllChecksPolicy.NAME, AllChecksPolicy::new);

    private ScoringPolicies() {
    }

    public static ScoringPolicy byName(String name) {
        Supplier<ScoringPolicy> supplier = POLICIES.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException(
                    "Unknown scoring policy '" + name + "'. Use one of " + POLICIES.keySet());
        }
        return supplier.get();
    }
}
