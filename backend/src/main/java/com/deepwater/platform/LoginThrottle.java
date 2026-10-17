package com.deepwater.platform;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limits failed password sign-ins per email so a password cannot be guessed by brute force.
 * State is in memory: it resets on restart and is per instance, which suits the single-instance MVP.
 */
@Component
class LoginThrottle {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_EMAILS = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    LoginThrottle() { this(Clock.systemUTC()); }
    LoginThrottle(Clock clock) { this.clock = clock; }

    boolean isBlocked(String email) {
        Deque<Instant> recent = failures.get(email);
        if (recent == null) return false;
        synchronized (recent) {
            prune(recent);
            return recent.size() >= MAX_FAILURES;
        }
    }

    void recordFailure(String email) {
        if (failures.size() >= MAX_TRACKED_EMAILS) evictExpired();
        Deque<Instant> recent = failures.computeIfAbsent(email, key -> new ArrayDeque<>());
        synchronized (recent) {
            prune(recent);
            recent.addLast(clock.instant());
        }
    }

    void recordSuccess(String email) { failures.remove(email); }

    private void prune(Deque<Instant> recent) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) recent.removeFirst();
    }

    private void evictExpired() {
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue());
                return entry.getValue().isEmpty();
            }
        });
    }
}
