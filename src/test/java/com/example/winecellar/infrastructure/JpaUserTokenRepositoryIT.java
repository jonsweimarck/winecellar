package com.example.winecellar.infrastructure;

import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;
import com.example.winecellar.support.SharedPostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** WINE-59: den atomära engångsgrinden och det unika indexet, mot riktig Postgres. */
@SpringBootTest
class JpaUserTokenRepositoryIT extends SharedPostgres {

    private static final UserId USER = new UserId(987654L);

    @Autowired
    private JpaUserTokenRepository repository;

    @AfterEach
    void städa() {
        repository.deleteAllByUser(USER);
    }

    @Test
    void baraEnAvFleraSamtidigaRadering​arSkaLyckas() throws Exception {
        UserToken token = repository.save(
                new UserToken(null, USER, Purpose.PASSWORD_RESET, "hash-atomar-1", Instant.now().plusSeconds(60), null));
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> repository.deleteById(token.id())));
            }
            int wins = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) {
                    wins++;
                }
            }
            assertThat(wins).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void skaNekaTvåÅterställningstokensMenTillåtaFleraVerifieringstokensFörSammaAnvändare() {
        repository.save(new UserToken(null, USER, Purpose.EMAIL_VERIFICATION, "hash-unik-1",
                Instant.now().plusSeconds(60), "pending-1"));
        repository.save(new UserToken(null, USER, Purpose.EMAIL_VERIFICATION, "hash-unik-2",
                Instant.now().plusSeconds(60), "pending-2"));
        assertThat(repository.findByUserAndPurpose(USER, Purpose.EMAIL_VERIFICATION))
                .extracting(UserToken::pendingPasswordHash).containsExactly("pending-1", "pending-2");

        repository.save(new UserToken(null, USER, Purpose.PASSWORD_RESET, "hash-unik-3",
                Instant.now().plusSeconds(60), null));
        assertThatThrownBy(() -> repository.save(new UserToken(null, USER, Purpose.PASSWORD_RESET,
                "hash-unik-4", Instant.now().plusSeconds(60), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
