package com.deepwater.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {
    @Test
    void blockLiftsOnceFailuresAreOlderThanTheWindow() {
        var clock = new MutableClock(Instant.parse("2026-10-17T12:00:00Z"));
        var throttle = new LoginThrottle(clock);
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) throttle.recordFailure("a@example.test");
        assertThat(throttle.isBlocked("a@example.test")).isTrue();
        assertThat(throttle.isBlocked("b@example.test")).isFalse();

        clock.now = clock.now.plus(LoginThrottle.WINDOW).plusSeconds(1);
        assertThat(throttle.isBlocked("a@example.test")).isFalse();
    }

    private static final class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now) { this.now = now; }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
