package com.palavecino.backend.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Test-only {@link Clock} whose instant can be moved forward, letting tests simulate the passage
 * of time (e.g. for the email-resend cooldown) without sleeping. Tests that use it reset the
 * clock to "now" in {@code @BeforeEach} so the shared bean never leaks state between tests.
 */
public class AdvanceableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public AdvanceableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new AdvanceableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    /** Moves the clock forward by the given duration. */
    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    /** Resets the clock to the current real time (used in {@code @BeforeEach}). */
    public void resetToNow() {
        this.instant = Instant.now();
    }
}
