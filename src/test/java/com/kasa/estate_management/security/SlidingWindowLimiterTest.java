package com.kasa.estate_management.security;

import com.kasa.estate_management.security.SlidingWindowLimiter.Decision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Instant t0 = Instant.parse("2026-11-09T10:00:00Z");

    @Test
    void allowsUpToTheLimitThenRefuses() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, WINDOW, 100);

        assertThat(limiter.tryAcquire("a", t0).allowed()).isTrue();
        assertThat(limiter.tryAcquire("a", t0).allowed()).isTrue();
        assertThat(limiter.tryAcquire("a", t0).allowed()).isTrue();

        Decision refused = limiter.tryAcquire("a", t0);
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isBetween(1L, 901L);
    }

    @Test
    void announcesTheFirstBlockOnlyOnce() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(1, WINDOW, 100);
        limiter.tryAcquire("a", t0);

        assertThat(limiter.tryAcquire("a", t0).firstBlock()).isTrue();
        assertThat(limiter.tryAcquire("a", t0).firstBlock()).isFalse();
        assertThat(limiter.tryAcquire("a", t0.plusSeconds(5)).firstBlock()).isFalse();

        // After the window passes the key recovers, so a later block is announced again.
        Instant later = t0.plus(WINDOW).plusSeconds(1);
        assertThat(limiter.tryAcquire("a", later).allowed()).isTrue();
        assertThat(limiter.tryAcquire("a", later).firstBlock()).isTrue();
    }

    @Test
    void slotsFreeUpOnceTheWindowHasPassed() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, WINDOW, 100);
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a", t0);
        }
        assertThat(limiter.tryAcquire("a", t0.plusSeconds(60)).allowed()).isFalse();

        assertThat(limiter.tryAcquire("a", t0.plus(WINDOW).plusSeconds(1)).allowed()).isTrue();
    }

    @Test
    void keysAreCountedIndependently() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(1, WINDOW, 100);

        assertThat(limiter.tryAcquire("jane", t0).allowed()).isTrue();
        assertThat(limiter.tryAcquire("jane", t0).allowed()).isFalse();
        assertThat(limiter.tryAcquire("peter", t0).allowed()).isTrue();
    }

    @Test
    void releasingTheLastSlotGivesItBack() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(2, WINDOW, 100);
        limiter.tryAcquire("a", t0);
        limiter.tryAcquire("a", t0);
        assertThat(limiter.tryAcquire("a", t0).allowed()).isFalse();

        limiter.releaseLast("a");

        assertThat(limiter.tryAcquire("a", t0).allowed()).isTrue();
    }

    @Test
    void clearingAKeyResetsItsCounter() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(1, WINDOW, 100);
        limiter.tryAcquire("a", t0);
        assertThat(limiter.tryAcquire("a", t0).allowed()).isFalse();

        limiter.clear("a");

        assertThat(limiter.tryAcquire("a", t0).allowed()).isTrue();
    }

    @Test
    void whenTheTableIsFullNewKeysAreLetThroughUntracked() {
        // Documents a deliberate trade-off: under a flood of distinct keys we stop tracking new
        // ones rather than run out of memory, while keys already tracked keep being limited.
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(1, WINDOW, 2);
        limiter.tryAcquire("a", t0);
        limiter.tryAcquire("b", t0);

        assertThat(limiter.tryAcquire("c", t0).allowed()).isTrue();
        assertThat(limiter.tryAcquire("c", t0).allowed()).isTrue();
        assertThat(limiter.tryAcquire("a", t0).allowed()).isFalse();
    }
}