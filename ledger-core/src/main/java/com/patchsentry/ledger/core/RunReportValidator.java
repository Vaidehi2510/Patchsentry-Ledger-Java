package com.patchsentry.ledger.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enforces the upload contract. Collects every problem instead of stopping at the
 * first one, so a runner can fix a bad report in a single round trip.
 */
public final class RunReportValidator {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CHECKS = 200;
    public static final int MAX_FINDINGS = 100;
    public static final int MAX_LIMITATIONS = 50;
    public static final int MAX_TITLE = 300;
    public static final int MAX_TEXT = 4000;

    private static final Pattern RECORD_ID = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern REVISION = Pattern.compile("[a-f0-9]{40}");
    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}");
    private static final Pattern CHECK_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,100}");

    public List<String> problems(RunReport report) {
        List<String> errors = new ArrayList<>();
        if (report == null) {
            errors.add("report is required");
            return errors;
        }
        if (report.schemaVersion() != SCHEMA_VERSION) {
            errors.add("schemaVersion must be " + SCHEMA_VERSION);
        }
        if (!matches(RECORD_ID, report.recordId())) {
            errors.add("recordId must be 64 lowercase hex characters");
        }
        if (!matches(REPOSITORY, report.repository())) {
            errors.add("repository must look like owner/name");
        }
        if (report.prNumber() < 1) {
            errors.add("prNumber must be positive");
        }
        if (!matches(REVISION, report.revision())) {
            errors.add("revision must be a full 40-character commit SHA");
        }
        checkLength(errors, "prTitle", report.prTitle(), MAX_TITLE);
        checkChecks(errors, report.checks());
        if (report.findings().size() > MAX_FINDINGS) {
            errors.add("at most " + MAX_FINDINGS + " findings are allowed");
        }
        for (int i = 0; i < report.findings().size(); i++) {
            Finding finding = report.findings().get(i);
            if (finding == null || isBlank(finding.title()) || finding.severity() == null) {
                errors.add("findings[" + i + "] needs a title and a severity");
                continue;
            }
            checkLength(errors, "findings[" + i + "].description", finding.description(), MAX_TEXT);
            checkLength(errors, "findings[" + i + "].suggestedFix", finding.suggestedFix(), MAX_TEXT);
        }
        if (report.limitations().size() > MAX_LIMITATIONS) {
            errors.add("at most " + MAX_LIMITATIONS + " limitations are allowed");
        }
        if (report.ai() != null && report.ai().costUsd() != null && report.ai().costUsd().signum() < 0) {
            errors.add("ai.costUsd cannot be negative");
        }
        return errors;
    }

    public void validate(RunReport report) {
        List<String> errors = problems(report);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static void checkChecks(List<String> errors, List<CheckResult> checks) {
        if (checks.isEmpty()) {
            errors.add("at least one check is required");
            return;
        }
        if (checks.size() > MAX_CHECKS) {
            errors.add("at most " + MAX_CHECKS + " checks are allowed");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < checks.size(); i++) {
            CheckResult check = checks.get(i);
            String where = "checks[" + i + "]";
            if (check == null) {
                errors.add(where + " is empty");
                continue;
            }
            if (!matches(CHECK_ID, check.id())) {
                errors.add(where + ".id is missing or has invalid characters");
            } else if (!seen.add(check.id())) {
                errors.add(where + ".id '" + check.id() + "' is duplicated");
            }
            if (check.method() == null) {
                errors.add(where + ".method is required");
            }
            if (check.status() == null) {
                errors.add(where + ".status is required");
            }
            checkLength(errors, where + ".name", check.name(), MAX_TITLE);
            checkLength(errors, where + ".details", check.details(), MAX_TEXT);
        }
    }

    private static void checkLength(List<String> errors, String field, String value, int max) {
        if (value != null && value.length() > max) {
            errors.add(field + " exceeds " + max + " characters");
        }
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
