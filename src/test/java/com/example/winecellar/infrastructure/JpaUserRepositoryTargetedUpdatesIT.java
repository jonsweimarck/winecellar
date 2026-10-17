package com.example.winecellar.infrastructure;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.support.SharedPostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WINE-65: riktade uppdateringar av lösenordshash/verifierad-flagga mot riktig Postgres. En
 * samtidig ändring av senaste login eller adminflaggan får inte skrivas över av en gammal kopia av
 * User (vilket en hel-User-sparning hade gjort).
 */
@SpringBootTest
class JpaUserRepositoryTargetedUpdatesIT extends SharedPostgres {

    @Autowired
    private UserRepository userRepository;

    private UserId unverifiedId;
    private UserId verifiedId;

    @BeforeEach
    void skapaAnvändare() {
        Instant created = Instant.parse("2026-01-01T10:00:00Z");
        unverifiedId = userRepository.save(new User(null, "targeted-unverified@example.com", "{noop}old", created,
                1, false, false, created, false)).id();
        verifiedId = userRepository.save(new User(null, "targeted-verified@example.com", "{noop}old", created,
                1, false, false, created, true)).id();
    }

    @AfterEach
    void städa() {
        userRepository.deleteById(unverifiedId);
        userRepository.deleteById(verifiedId);
    }

    @Test
    void aktiveringSkaInteSkrivaÖverEnSamtidigÄndringAvSenasteLoginEllerAdmin() {
        User stale = userRepository.findById(unverifiedId).orElseThrow();
        Instant later = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        userRepository.updateLastLogin(unverifiedId, later);
        userRepository.save(userRepository.findById(unverifiedId).orElseThrow().withAdmin(true));

        assertThat(userRepository.activateAccount(stale.id(), "{noop}nytt")).isTrue();

        User after = userRepository.findById(unverifiedId).orElseThrow();
        assertThat(after.hashedPassword()).isEqualTo("{noop}nytt");
        assertThat(after.emailVerified()).isTrue();
        assertThat(after.admin()).isTrue();
        assertThat(after.lastLoginAt()).isEqualTo(later);
    }

    @Test
    void aktiveringSkaInteÄndraEttRedanVerifieratKonto() {
        assertThat(userRepository.activateAccount(verifiedId, "{noop}angripare")).isFalse();

        assertThat(userRepository.findById(verifiedId).orElseThrow().hashedPassword()).isEqualTo("{noop}old");
    }

    @Test
    void villkoradRaderingSkaBaraRaderaEttFortfarandeOverifieratKonto() {
        assertThat(userRepository.deleteIfUnverified(verifiedId)).isFalse();
        assertThat(userRepository.findById(verifiedId)).isPresent();

        assertThat(userRepository.deleteIfUnverified(unverifiedId)).isTrue();
        assertThat(userRepository.findById(unverifiedId)).isEmpty();
    }

    @Test
    void lösenordsbyteSkaBaraÄndraHashenOchRapporteraOmAnvändarenFinns() {
        Instant later = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        userRepository.updateLastLogin(verifiedId, later);
        userRepository.save(userRepository.findById(verifiedId).orElseThrow().withAdmin(true));

        assertThat(userRepository.updateHashedPassword(verifiedId, "{noop}reset")).isTrue();

        User after = userRepository.findById(verifiedId).orElseThrow();
        assertThat(after.hashedPassword()).isEqualTo("{noop}reset");
        assertThat(after.admin()).isTrue();
        assertThat(after.lastLoginAt()).isEqualTo(later);
        assertThat(userRepository.updateHashedPassword(new UserId(-1L), "{noop}x")).isFalse();
    }
}
