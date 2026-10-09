package com.example.winecellar.infrastructure;

import com.example.winecellar.application.TokenService;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.UserToken.Purpose;
import com.example.winecellar.support.SharedPostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WINE-59: bevisar mot riktig Postgres och den riktiga transaktionsstacken vad ett
 * förlorat race på det unika indexet (user_id, purpose) faktiskt kastar ur
 * TokenService.issue - det är den typen RegistrationService/PasswordResetService fångar.
 * Efter varje runda finns exakt ett token kvar (aldrig två giltiga).
 */
@SpringBootTest
class TokenIssueRaceIT extends SharedPostgres {

    private static final UserId USER = new UserId(987655L);
    private static final int THREADS = 8;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void städa() {
        jdbcTemplate.update("delete from user_tokens where user_id = ?", USER.value());
    }

    @Test
    void förloradeRaceSkaGeDataIntegrityViolationExceptionOchAldrigTvåTokens() throws Exception {
        var pool = Executors.newFixedThreadPool(THREADS);
        List<Throwable> failures = new ArrayList<>();
        int succeeded = 0;
        try {
            for (int round = 0; round < 30; round++) {
                jdbcTemplate.update("delete from user_tokens where user_id = ?", USER.value());
                CyclicBarrier barrier = new CyclicBarrier(THREADS);
                List<Future<String>> futures = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    futures.add(pool.submit(() -> {
                        barrier.await();
                        return tokenService.issue(USER, Purpose.EMAIL_VERIFICATION);
                    }));
                }
                for (Future<String> future : futures) {
                    try {
                        future.get();
                        succeeded++;
                    } catch (java.util.concurrent.ExecutionException e) {
                        failures.add(e.getCause());
                    }
                }
                assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from user_tokens where user_id = ? and purpose = 'EMAIL_VERIFICATION'",
                        Integer.class, USER.value())).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(succeeded).isPositive();
        // Allt som misslyckas ska vara just den typ som anroparna fångar.
        assertThat(failures).allSatisfy(t -> assertThat(t).isInstanceOf(DataIntegrityViolationException.class));
    }
}
