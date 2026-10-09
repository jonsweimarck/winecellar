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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
 * WINE-59: ett DETERMINISTISKT race mot det riktiga unika constraintet (user_id, purpose) - ett
 * token per användare och syfte, för både verifiering och återställning - i den riktiga
 * transaktionsstacken. Tråd A infogar ett token i en öppen, ännu ej committad transaktion. Tråd B
 * anropar då TokenService.issue: dess radering ser inte A:s rad, dess insert blockerar på
 * indexet och misslyckas efter A:s commit - alltid, så racet OBSERVERAS i stället för att
 * hoppas på tur.
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

    @ParameterizedTest
    @EnumSource(Purpose.class)
    void förloratRaceSkaGeDataIntegrityViolationException(Purpose purpose) throws Exception {
        CountDownLatch insertedByA = new CountDownLatch(1);
        CountDownLatch bHasStarted = new CountDownLatch(1);

        CompletableFuture<Void> a = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    tokenRepository.save(new UserToken(null, userId, purpose, "race-hash-a",
                            Instant.now().plusSeconds(60)));
                    insertedByA.countDown();
                    await(bHasStarted);
                    sleep(1000); // B hinner blockera på indexet innan A committar
                }));
        assertThat(insertedByA.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<String> b = CompletableFuture.supplyAsync(() -> {
            bHasStarted.countDown();
            return tokenService.issue(userId, purpose);
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
                "select count(*) from user_tokens where user_id = ? and purpose = ?",
                Integer.class, userId.value(), purpose.name())).isEqualTo(1);
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
