package com.patchsentry.ledger.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RunnerTokensTest {

    @Test
    void issuedTokensAreWellFormedUniqueAndStoredOnlyAsHashes() {
        RunnerTokens.IssuedToken first = RunnerTokens.issue();
        RunnerTokens.IssuedToken second = RunnerTokens.issue();
        assertTrue(RunnerTokens.looksValid(first.plaintext()));
        assertNotEquals(first.plaintext(), second.plaintext());
        assertEquals(64, first.hash().length());
        assertEquals(RunnerTokens.hash(first.plaintext()), first.hash());
        assertTrue(first.plaintext().startsWith(first.displayPrefix()));
    }

    @Test
    void toStringNeverLeaksTheSecret() {
        RunnerTokens.IssuedToken token = RunnerTokens.issue();
        assertFalse(token.toString().contains(token.plaintext()));
    }

    @Test
    void rejectsMalformedTokensBeforeAnyLookup() {
        assertFalse(RunnerTokens.looksValid(null));
        assertFalse(RunnerTokens.looksValid("psl_short"));
        assertFalse(RunnerTokens.looksValid("ghp_" + "a".repeat(43)));
    }

    @Test
    void constantTimeEqualsHandlesNulls() {
        assertTrue(RunnerTokens.constantTimeEquals("secret", "secret"));
        assertFalse(RunnerTokens.constantTimeEquals("secret", "Secret"));
        assertFalse(RunnerTokens.constantTimeEquals(null, "secret"));
    }
}
