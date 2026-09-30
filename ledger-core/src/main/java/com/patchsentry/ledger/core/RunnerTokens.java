package com.patchsentry.ledger.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Issues and hashes runner tokens. Only the SHA-256 hash is stored; the plaintext
 * is shown once. Tokens have 256 bits of entropy, so a fast hash is appropriate
 * (unlike passwords, which need a slow KDF).
 */
public final class RunnerTokens {

    public static final String PREFIX = "psl_";
    private static final Pattern FORMAT = Pattern.compile("psl_[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private RunnerTokens() {
    }

    public record IssuedToken(String plaintext, String displayPrefix, String hash) {
        @Override
        public String toString() {
            // Never let the secret reach logs through toString().
            return "IssuedToken[displayPrefix=" + displayPrefix + "]";
        }
    }

    public static IssuedToken issue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String plaintext = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedToken(plaintext, plaintext.substring(0, 10), hash(plaintext));
    }

    /** Cheap syntax check before touching the database. */
    public static boolean looksValid(String candidate) {
        return candidate != null && FORMAT.matcher(candidate).matches();
    }

    public static String hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    /** Constant-time comparison for shared secrets such as the admin token. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
