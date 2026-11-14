package com.citynavigator.external;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Keeps calls to a public service at least an interval apart, as their usage policies ask. Each caller reserves the
 * next free slot and waits for it; a caller that would have to wait too long gives up instead.
 */
public final class MinIntervalLimiter {

    private final long intervalNanos;
    private final long maxWaitNanos;
    private long nextSlot;

    public MinIntervalLimiter(Duration interval, Duration maxWait) {
        this.intervalNanos = interval.toNanos();
        this.maxWaitNanos = maxWait.toNanos();
        this.nextSlot = System.nanoTime();
    }

    public void acquire() {
        long wait;
        synchronized (this) {
            long now = System.nanoTime();
            long slot = nextSlot - now > 0 ? nextSlot : now;
            wait = slot - now;
            if (wait > maxWaitNanos) {
                throw new ExternalServiceException("too many requests are already waiting for the service");
            }
            nextSlot = slot + intervalNanos;
        }
        if (wait > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExternalServiceException("interrupted while waiting for the service", e);
            }
        }
    }
}
