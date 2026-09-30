package com.patchsentry.ledger.service;

/** The request is well formed but contradicts existing state (HTTP 409). */
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
