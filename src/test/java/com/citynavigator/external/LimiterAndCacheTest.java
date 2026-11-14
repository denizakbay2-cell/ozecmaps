package com.citynavigator.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LimiterAndCacheTest {

    @Test
    void theLimiterSpacesCallsAndRefusesLongWaits() {
        MinIntervalLimiter limiter = new MinIntervalLimiter(Duration.ofMillis(60), Duration.ofMillis(100));
        long started = System.nanoTime();
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(110));
        limiter.acquire();
        MinIntervalLimiter strict = new MinIntervalLimiter(Duration.ofSeconds(10), Duration.ofMillis(1));
        strict.acquire();
        assertThatThrownBy(strict::acquire).isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void theCacheForgetsOldAndLeastUsedEntries() {
        MutableClock clock = new MutableClock();
        TtlCache<String, Integer> cache = new TtlCache<>(2, Duration.ofMinutes(10), clock);
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.computeIfAbsent("a", loads::incrementAndGet)).isEqualTo(1);
        assertThat(cache.computeIfAbsent("a", loads::incrementAndGet)).isEqualTo(1);
        cache.put("b", 2);
        cache.get("a");
        cache.put("c", 3);
        assertThat(cache.get("b")).isEmpty();
        assertThat(cache.get("a")).contains(1);
        assertThat(cache.size()).isEqualTo(2);

        clock.now = clock.now.plus(Duration.ofMinutes(10));
        assertThat(cache.get("a")).isEmpty();
        assertThat(loads).hasValue(1);
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-14T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
