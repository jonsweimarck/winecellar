package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Städar övergivna rester av e-postverifieringen (WINE-65, se ADR 0026): utgångna rader i
 * {@code user_tokens} och overifierade konton som är äldre än sju dagar. Verifierade konton,
 * färska overifierade konton och giltiga tokens rörs aldrig. Ett overifierat konto kan inte ha
 * loggat in, så det äger inga viner eller konversationer; dess egna tokens raderas explicit
 * (ingen FK mot users, jfr AdminService.deleteUser). Triggas av en lyckad inloggning
 * (se web.StaleAccountCleanupTrigger), samma mönster som övergivna importmappar (ADR 0017).
 */
@Service
public class StaleAccountCleanup {

    /** Hur länge ett overifierat konto får ligga kvar innan det städas bort. */
    public static final Duration UNVERIFIED_ACCOUNT_MAX_AGE = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final Clock clock;

    public StaleAccountCleanup(UserRepository userRepository, UserTokenRepository tokenRepository, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    @Transactional
    public void cleanup() {
        var now = clock.instant();
        tokenRepository.deleteExpired(now);
        for (User stale : userRepository.findUnverifiedCreatedBefore(now.minus(UNVERIFIED_ACCOUNT_MAX_AGE))) {
            tokenRepository.deleteAllByUser(stale.id());
            userRepository.deleteById(stale.id());
        }
    }
}
