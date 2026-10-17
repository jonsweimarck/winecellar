package com.example.winecellar.application;

import com.example.winecellar.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** WINE-65: globalt tak för utgående mail, timme och dygn. */
class MailBudgetTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void skaNekaNärTimtaketÄrNått() {
        MailBudget budget = new MailBudget(3, 100, clock);
        for (int i = 0; i < 3; i++) {
            assertThat(budget.tryAcquire()).isTrue();
        }
        assertThat(budget.tryAcquire()).isFalse();
    }

    @Test
    void skaNekaNärDygnstaketÄrNåttÄvenOmTimfönstretÄrNytt() {
        MailBudget budget = new MailBudget(100, 3, clock);
        for (int i = 0; i < 3; i++) {
            assertThat(budget.tryAcquire()).isTrue();
        }
        clock.advance(Duration.ofHours(2));
        assertThat(budget.tryAcquire()).isFalse();
    }

    @Test
    void skaÅterställaTimtaketNärKlockanFlyttatsFörbiFönstretMenInteDygnstaketFörrän24Timmar() {
        MailBudget budget = new MailBudget(2, 3, clock);
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isFalse();

        clock.advance(Duration.ofMinutes(61));
        assertThat(budget.tryAcquire()).isTrue();   // tredje mailet på dygnet
        clock.advance(Duration.ofMinutes(61));
        assertThat(budget.tryAcquire()).isFalse();  // dygnstaket (3) nått

        clock.advance(Duration.ofHours(24));
        assertThat(budget.tryAcquire()).isTrue();
    }

    @Test
    void skaInteFörbrukaNågotDygnsutrymmeNärTimtaketNekar() {
        MailBudget budget = new MailBudget(1, 2, clock);
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(budget.tryAcquire()).isFalse();
        assertThat(budget.tryAcquire()).isFalse();
        clock.advance(Duration.ofMinutes(61));
        // Hade de nekade försöken räknats mot dygnet vore det nu slut
        assertThat(budget.tryAcquire()).isTrue();
    }

    @Test
    void skaÅterbetalaEnPlatsNärDenLämnasTillbaka() {
        MailBudget budget = new MailBudget(1, 1, clock);
        assertThat(budget.tryAcquire()).isTrue();
        budget.release();
        assertThat(budget.tryAcquire()).isTrue();
    }

    @Test
    void skaKlippaTimtaketSåAttPerAdressKartornaInteKanFyllasAvLevandePoster() {
        MailBudget budget = new MailBudget(1_000_000, 1_000_000, clock);
        assertThat(budget.hourlyCap()).isLessThan(RequestRateLimiter.DEFAULT_MAX_KEYS);
    }

    @Test
    void skaAvvisaTakUnderEtt() {
        assertThatThrownBy(() -> new MailBudget(0, 10, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MailBudget(10, 0, clock)).isInstanceOf(IllegalArgumentException.class);
    }
}
