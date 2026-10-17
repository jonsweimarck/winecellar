package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;
import com.example.winecellar.infrastructure.InMemoryUserRepository;
import com.example.winecellar.infrastructure.InMemoryUserTokenRepository;
import com.example.winecellar.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** WINE-65: städningens urval och strypning. */
class StaleAccountCleanupTest {

    private final MutableClock clock = new MutableClock();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryUserTokenRepository tokens = new InMemoryUserTokenRepository();
    private final StaleAccountCleanup cleanup = new StaleAccountCleanup(users, tokens, clock);

    private User user(String name, int daysOld, boolean verified) {
        Instant created = clock.instant().minus(Duration.ofDays(daysOld));
        return users.save(new User(null, name, "h", created, 1, false, false, created, verified));
    }

    private void token(User user, Duration validFor) {
        tokens.save(new UserToken(null, user.id(), Purpose.EMAIL_VERIFICATION, "hash-" + user.username(),
                clock.instant().plus(validFor)));
    }

    @Test
    void skaInteRaderaEttGammaltOverifieratKontoMedEttFortfarandeGiltigtToken() {
        User renewed = user("ny-lank@example.com", 8, false);   // nytt token "dag 6,9" => giltigt
        token(renewed, Duration.ofHours(20));
        User abandoned = user("overgiven@example.com", 8, false);
        token(abandoned, Duration.ofMinutes(-5));

        cleanup.cleanup();

        assertThat(users.findByUsername("ny-lank@example.com")).isPresent();
        assertThat(tokens.existsByUser(renewed.id())).isTrue();
        assertThat(users.findByUsername("overgiven@example.com")).isEmpty();
        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void skaAldrigRöraVerifieradeEllerFärskaKonton() {
        user("veteran@example.com", 400, true);
        user("farsk@example.com", 6, false);

        cleanup.cleanup();

        assertThat(users.findAll()).hasSize(2);
    }

    @Test
    void skaHögstStädaEnGångPerTimme() {
        cleanup.cleanup();
        user("gammal@example.com", 8, false);

        cleanup.cleanup();
        assertThat(users.findByUsername("gammal@example.com")).isPresent();

        clock.advance(Duration.ofMinutes(61));
        cleanup.cleanup();
        assertThat(users.findByUsername("gammal@example.com")).isEmpty();
    }
}
