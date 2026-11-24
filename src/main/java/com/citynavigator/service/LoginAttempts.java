package com.citynavigator.service;

import com.citynavigator.exception.TooManyAttemptsException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after {@link #MAX_FAILURES} wrong passwords for one address within
 * {@link #WINDOW}, signing in to it waits until the oldest of them is that old. Kept in memory: a restart forgets it,
 * which only gives a guesser a few more tries.
 */
@ApplicationScoped
public class LoginAttempts {

    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private Clock clock;

    protected LoginAttempts() {
        // for the CDI proxy
    }

    @Inject
    public LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    /** Refuses when the address has had too many wrong passwords lately. */
    public void check(String email) {
        Deque<Instant> recent = failures.get(email);
        if (recent == null) {
            return;
        }
        synchronized (recent) {
            forgetOld(recent);
            if (recent.size() >= MAX_FAILURES) {
                long seconds = Duration.between(clock.instant(), recent.peekFirst().plus(WINDOW)).toSeconds();
                long minutes = Math.max(1, (seconds + 59) / 60);
                throw new TooManyAttemptsException("Çok fazla yanlış deneme; " + minutes + " dakika sonra tekrar "
                        + "deneyin");
            }
        }
    }

    public void failed(String email) {
        Deque<Instant> recent = failures.computeIfAbsent(email, key -> new ArrayDeque<>());
        synchronized (recent) {
            forgetOld(recent);
            recent.addLast(clock.instant());
        }
    }

    public void succeeded(String email) {
        failures.remove(email);
    }

    private void forgetOld(Deque<Instant> recent) {
        Instant since = clock.instant().minus(WINDOW);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(since)) {
            recent.removeFirst();
        }
    }
}
