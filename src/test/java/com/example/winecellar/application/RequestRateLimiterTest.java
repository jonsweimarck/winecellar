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

    @Test
    void hasCapacitySkaFöljaSammaRegelSomTryAcquireUtanAttFörbruka() {
        MutableClock clock = new MutableClock();
        RequestRateLimiter limiter = new RequestRateLimiter(2, Duration.ofHours(1), clock);

        // Ändrar ingenting: hur många gånger som helst
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.hasCapacity("a")).isTrue();
        }
        assertThat(limiter.size()).isZero();

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.hasCapacity("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.hasCapacity("a")).isFalse();
        assertThat(limiter.hasCapacity("b")).isTrue();

        clock.advance(Duration.ofMinutes(61));
        assertThat(limiter.hasCapacity("a")).isTrue();
    }

    @Test
    void hasCapacitySkaRensaUtgångnaPosterIFullKartaPrecisSomTryAcquire() {
        MutableClock clock = new MutableClock();
        RequestRateLimiter limiter = new RequestRateLimiter(3, Duration.ofHours(1), clock, 2);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");

        // Full karta med levande poster: ingen plats för en ny nyckel (men befintliga fungerar)
        assertThat(limiter.hasCapacity("c")).isFalse();
        assertThat(limiter.hasCapacity("a")).isTrue();

        // När posterna gått ut rensas de, och en ny nyckel får plats
        clock.advance(Duration.ofMinutes(61));
        assertThat(limiter.hasCapacity("c")).isTrue();
        assertThat(limiter.tryAcquire("c")).isTrue();
    }

    @Test
    void releaseSkaÅterbetalaDenSenastFörbrukadePlatsen() {
        MutableClock clock = new MutableClock();
        RequestRateLimiter limiter = new RequestRateLimiter(1, Duration.ofHours(1), clock);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        limiter.release("a");
        assertThat(limiter.tryAcquire("a")).isTrue();
        // Okänd nyckel är ett no-op
        limiter.release("okänd");
    }

    @Test
    void sammaRegelSkaGällaTryAcquireOchHasCapacityNärEnNyckelFinnsMenAllaPosterGåttUt() {
        MutableClock clock = new MutableClock();
        RequestRateLimiter limiter = new RequestRateLimiter(1, Duration.ofHours(1), clock, 1);
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMinutes(61));

        assertThat(limiter.hasCapacity("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
    }
}
