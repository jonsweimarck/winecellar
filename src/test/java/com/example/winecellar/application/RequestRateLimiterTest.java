package com.example.winecellar.application;

import com.example.winecellar.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RequestRateLimiterTest {

    @Test
    void skaTillåtaHögstDetKonfigureradeAntaletPerNyckelOchFönster() {
        MutableClock clock = new MutableClock();
        RequestRateLimiter limiter = new RequestRateLimiter(3, Duration.ofHours(1), clock);

        assertThat(limiter.tryAcquire("a@example.com")).isTrue();
        assertThat(limiter.tryAcquire("a@example.com")).isTrue();
        assertThat(limiter.tryAcquire("a@example.com")).isTrue();
        assertThat(limiter.tryAcquire("a@example.com")).isFalse();
        // Annan nyckel påverkas inte
        assertThat(limiter.tryAcquire("b@example.com")).isTrue();

        clock.advance(Duration.ofMinutes(61));
        assertThat(limiter.tryAcquire("a@example.com")).isTrue();
    }

    @Test
    void skaHållaKartanStorleksbegränsadOchNekaNyaNycklarTystNärDenÄrFull() {
        MutableClock clock = new MutableClock();
        RequestRateLimiter limiter = new RequestRateLimiter(3, Duration.ofHours(1), clock, 2);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("b")).isTrue();
        assertThat(limiter.tryAcquire("c")).isFalse();
        assertThat(limiter.size()).isEqualTo(2);
        // Befintliga nycklar fungerar fortfarande
        assertThat(limiter.tryAcquire("a")).isTrue();

        // Utgångna poster rensas, så ny nyckel får plats igen
        clock.advance(Duration.ofMinutes(61));
        assertThat(limiter.tryAcquire("c")).isTrue();
        assertThat(limiter.size()).isLessThanOrEqualTo(2);
    }
}
