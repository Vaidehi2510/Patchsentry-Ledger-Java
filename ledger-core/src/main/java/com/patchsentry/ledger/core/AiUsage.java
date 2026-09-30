package com.patchsentry.ledger.core;

import java.math.BigDecimal;

/** Which model reviewed the run and what it cost. Optional on a report. */
public record AiUsage(String backend, String model, BigDecimal costUsd, int calls) {
}
