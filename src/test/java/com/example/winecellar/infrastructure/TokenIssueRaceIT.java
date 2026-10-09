package com.example.winecellar.infrastructure;

import com.example.winecellar.application.TokenService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;
import com.example.winecellar.support.SharedPostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WINE-59: ett DETERMINISTISKT race mot det riktiga partiella unika indexet för
 * återställningstokens (ett åt gången per användare), i den riktiga transaktionsstacken.
 * Tråd A infogar ett återställningstoken i en öppen, ännu ej committad transaktion. Tråd B
 * anropar då TokenService.issue: dess radering ser inte A:s rad, dess insert blockerar på
 * indexet och misslyckas efter A:s commit - alltid, så racet OBSERVERAS i stället för att
 * hoppas på tur. Verifieringstokens har inget sådant index (flera tillåts, ADR 0026).
 */
@SpringBootTest
class TokenIssueRaceIT extends SharedPostgres {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JpaUserTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId userId;

    @BeforeEach
    void skapaAnvändare() {
        Instant now = Instant.now();
        userId = userRepository.save(new User(null, "race-it@example.com", "hash", now, 1, false, false, now, true))
                .id();
    }

    @AfterEach
    void städa() {
        tokenRepository.deleteAllByUser(userId);
        userRepository.deleteById(userId);
    }

    @Test
    void förloradeRaceFörÅterställningstokenSkaGeDataIntegrityViolationException() throws Exception {
        CountDownLatch insertedByA = new CountDownLatch(1);
        CountDownLatch bHasStarted = new CountDownLatch(1);

        CompletableFuture<Void> a = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    tokenRepository.save(new UserToken(null, userId, Purpose.PASSWORD_RESET, "race-hash-a",
                            Instant.now().plusSeconds(60), null));
                    insertedByA.countDown();
                    await(bHasStarted);
                    sleep(1000); // B hinner blockera på indexet innan A committar
                }));
        assertThat(insertedByA.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<String> b = CompletableFuture.supplyAsync(() -> {
            bHasStarted.countDown();
            return tokenService.issue(userId, Purpose.PASSWORD_RESET);
        });

        a.get(20, TimeUnit.SECONDS);
        Throwable failure = null;
        try {
            b.get(20, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            failure = e.getCause();
        }
        // Racet SKA ha observerats, och med exakt den typ som anroparna fångar.
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user_tokens where user_id = ? and purpose = 'PASSWORD_RESET'",
                Integer.class, userId.value())).isEqualTo(1);
    }

    @Test
    void flerVerifieringstokensFörSammaAnvändareSkaTillåtasOchHållasUnderTaket() {
        for (int i = 0; i < 5; i++) {
            tokenService.issueVerification(userId, "pending-" + i);
        }
        assertThat(tokenRepository.findByUserAndPurpose(userId, Purpose.EMAIL_VERIFICATION))
                .extracting(UserToken::pendingPasswordHash)
                .containsExactly("pending-2", "pending-3", "pending-4");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
