package com.patchsentry.ledger.core;

import java.io.Serial;
import java.util.List;

/** Thrown when a report breaks the upload contract. Carries every problem found. */
public class ValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<String> errors;

    public ValidationException(List<String> errors) {
        super("Invalid run report: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
