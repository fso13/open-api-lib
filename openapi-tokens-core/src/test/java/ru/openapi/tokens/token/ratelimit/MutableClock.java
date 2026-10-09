package ru.openapi.tokens.token.ratelimit;

import io.github.bucket4j.TimeMeter;

import java.time.Duration;

/**
 * Controllable {@link TimeMeter} for rate-limit window tests.
 */
final class MutableClock implements TimeMeter {

    private long nanos;

    void advance(Duration duration) {
        nanos += duration.toNanos();
    }

    @Override
    public long currentTimeNanos() {
        return nanos;
    }

    @Override
    public boolean isWallClockBased() {
        return false;
    }
}
