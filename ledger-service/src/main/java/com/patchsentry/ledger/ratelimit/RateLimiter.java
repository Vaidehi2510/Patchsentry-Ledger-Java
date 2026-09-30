package com.patchsentry.ledger.ratelimit;

/** Admission control for runner uploads. */
public interface RateLimiter {

    /** Returns {@code true} if the caller identified by {@code key} may proceed. */
    boolean tryAcquire(String key);
}
