package com.Reservation.Hotel.payment;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fraud protection for the checkout: after {@link #MAX_FAILURES} failed card attempts (invalid details or
 * declines) within {@link #WINDOW}, the guest has to wait before trying again. This stops someone testing
 * stolen card numbers one after another. A successful payment clears the counter.
 */
@Component
public class PaymentAttemptLimiter {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    /** Seconds the user still has to wait, or 0 when they may try again. */
    public long secondsLocked(String username, Instant now) {
        Deque<Instant> list = failures.get(key(username));
        if (list == null) return 0;
        synchronized (list) {
            prune(list, now);
            if (list.size() < MAX_FAILURES) return 0;
            return Math.max(1, Duration.between(now, list.peekFirst().plus(WINDOW)).toSeconds());
        }
    }

    public void recordFailure(String username, Instant now) {
        Deque<Instant> list = failures.computeIfAbsent(key(username), k -> new ArrayDeque<>());
        synchronized (list) {
            prune(list, now);
            list.addLast(now);
        }
    }

    /** Failed attempts left before the lock-out (shown as a warning on the page). */
    public int attemptsLeft(String username, Instant now) {
        Deque<Instant> list = failures.get(key(username));
        if (list == null) return MAX_FAILURES;
        synchronized (list) {
            prune(list, now);
            return Math.max(0, MAX_FAILURES - list.size());
        }
    }

    public void reset(String username) {
        failures.remove(key(username));
    }

    private static void prune(Deque<Instant> list, Instant now) {
        while (!list.isEmpty() && !list.peekFirst().plus(WINDOW).isAfter(now)) list.removeFirst();
    }

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase(java.util.Locale.ROOT);
    }
}
