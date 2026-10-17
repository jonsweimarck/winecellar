package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Städar övergivna rester av e-postverifieringen (WINE-65, se ADR 0026): utgångna rader i
 * {@code user_tokens} och overifierade konton som är äldre än sju dagar. Verifierade konton,
 * färska overifierade konton och giltiga tokens rörs aldrig - och inte heller ett gammalt
 * overifierat konto som fortfarande har ett giltigt verifieringstoken (användaren har begärt en ny
 * länk nyligen). Ett overifierat konto kan inte ha loggat in, så det äger inga viner eller
 * konversationer; dess egna tokens raderas explicit (ingen FK mot users, jfr AdminService.deleteUser).
 * Triggas av en lyckad inloggning (se web.StaleAccountCleanupTrigger), samma mönster som övergivna
 * importmappar (ADR 0017), men högst en gång per timme så att inte varje inloggning kör frågorna.
 */
@Service
public class StaleAccountCleanup {

    /** Hur länge ett overifierat konto får ligga kvar innan det städas bort. */
    public static final Duration UNVERIFIED_ACCOUNT_MAX_AGE = Duration.ofDays(7);
    /** Minsta tid mellan två städningar (i minnet; en omstart tillåter en direkt städning). */
    public static final Duration MIN_INTERVAL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final Clock clock;
    private Instant lastRun;

    public StaleAccountCleanup(UserRepository userRepository, UserTokenRepository tokenRepository, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    public void cleanup() {
        Instant now = clock.instant();
        synchronized (this) {
            if (lastRun != null && now.isBefore(lastRun.plus(MIN_INTERVAL))) {
                return;
            }
            // Tidsstämpeln sätts före arbetet: en städning som felar ska inte köras om vid VARJE inloggning.
            lastRun = now;
        }
        doCleanup(now);
    }

    private void doCleanup(Instant now) {
        tokenRepository.deleteExpired(now);
        for (User stale : userRepository.findUnverifiedCreatedBefore(now.minus(UNVERIFIED_ACCOUNT_MAX_AGE))) {
            // Efter att utgångna raderats är varje kvarvarande token giltigt: kontot används fortfarande.
            if (tokenRepository.existsByUser(stale.id())) {
                continue;
            }
            // Villkorad radering: en användare som hunnit verifieras sedan urvalet raderas inte.
            userRepository.deleteIfUnverified(stale.id());
        }
    }
}
