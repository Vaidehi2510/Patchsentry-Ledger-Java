package com.patchsentry.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed configuration bound from {@code ledger.*}. The admin API stays disabled
 * until an admin token is configured (secure default).
 */
@ConfigurationProperties(prefix = "ledger")
public record LedgerProperties(
        String adminToken,
        @DefaultValue("120") int rateLimitPerMinute,
        @DefaultValue("required-checks") String scoringPolicy) {

    public LedgerProperties {
        if (adminToken != null && !adminToken.isBlank() && adminToken.length() < 16) {
            throw new IllegalArgumentException("ledger.admin-token must be at least 16 characters");
        }
        if (rateLimitPerMinute < 1) {
            throw new IllegalArgumentException("ledger.rate-limit-per-minute must be positive");
        }
    }

    public boolean adminEnabled() {
        return adminToken != null && !adminToken.isBlank();
    }
}
