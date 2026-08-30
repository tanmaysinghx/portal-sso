package com.tanmaysinghx.portalsso.security.ratelimit;

/**
 * A single token bucket: {@code capacity} requests available, refilling continuously at
 * {@code refillPerSecond}.
 *
 * <p>Chosen over a fixed window because a fixed window lets a caller spend its whole allowance at
 * the end of one window and again at the start of the next — twice the intended rate across the
 * boundary. A bucket smooths that out while still permitting a short burst up to capacity, which is
 * what a human retyping a password actually looks like.
 */
final class TokenBucket {

    private final double capacity;
    private final double refillPerSecond;

    private double tokens;
    private long lastRefillNanos;

    TokenBucket(double capacity, double refillPerSecond, long nowNanos) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    /** @return true when a token was available and consumed. */
    synchronized boolean tryConsume(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** Whole seconds until the next token, for the {@code Retry-After} header. Never below 1. */
    synchronized long retryAfterSeconds(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0) {
            return 0;
        }
        double needed = 1.0 - tokens;
        return Math.max(1L, (long) Math.ceil(needed / refillPerSecond));
    }

    synchronized long lastUsedNanos() {
        return lastRefillNanos;
    }

    private void refill(long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        tokens = Math.min(capacity, tokens + (elapsed / 1_000_000_000.0) * refillPerSecond);
        lastRefillNanos = nowNanos;
    }
}
