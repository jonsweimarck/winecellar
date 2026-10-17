package com.example.winecellar.infrastructure;

import com.example.winecellar.application.PasswordResetService;
import com.example.winecellar.application.TokenOutcome;
import com.example.winecellar.application.TokenService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.UserToken.Purpose;
import com.example.winecellar.support.SharedPostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * WINE-65: lösenordsåterställningen är en transaktion mot riktig Postgres. Felar lösenordsbytet
 * EFTER att länken förbrukats rullas förbrukningen tillbaka: länken fungerar fortfarande och
 * lösenordet är oförändrat. Utan transaktion hade länken varit bränd utan att något ändrats.
 */
@SpringBootTest
class PasswordResetAtomicityIT extends SharedPostgres {

    private static final String ORIGINAL_HASH = "{noop}original";

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private TokenService tokenService;

    @SpyBean
    private UserRepository userRepository;

    private UserId userId;

    @BeforeEach
    void skapaAnvändare() {
        Instant now = Instant.now();
        userId = userRepository.save(new User(null, "atomicity-it@example.com", ORIGINAL_HASH, now, 1, false,
                false, now, true)).id();
    }

    @AfterEach
    void städa() {
        tokenService.revokeAll(userId, Purpose.PASSWORD_RESET);
        userRepository.deleteById(userId);
    }

    @Test
    void felEfterTokenförbrukningSkaRullaTillbakaAlltOchLämnaLänkenGiltig() {
        String raw = tokenService.issue(userId, Purpose.PASSWORD_RESET);
        doThrow(new IllegalStateException("simulerat fel efter tokenförbrukning"))
                .when(userRepository).updateHashedPassword(any(), any());

        assertThatThrownBy(() -> passwordResetService.resetPassword(raw, "nyttLösenord1"))
                .hasMessageContaining("simulerat fel efter tokenförbrukning");

        assertThat(tokenService.check(Purpose.PASSWORD_RESET, raw).outcome()).isEqualTo(TokenOutcome.SUCCESS);
        assertThat(userRepository.findById(userId).orElseThrow().hashedPassword()).isEqualTo(ORIGINAL_HASH);
    }

    @Test
    void lyckadÅterställningSkaBytaLösenordetOchFörbrukaLänken() {
        String raw = tokenService.issue(userId, Purpose.PASSWORD_RESET);

        assertThat(passwordResetService.resetPassword(raw, "nyttLösenord1")).isEqualTo(TokenOutcome.SUCCESS);

        assertThat(userRepository.findById(userId).orElseThrow().hashedPassword()).isNotEqualTo(ORIGINAL_HASH);
        assertThat(tokenService.check(Purpose.PASSWORD_RESET, raw).outcome()).isEqualTo(TokenOutcome.INVALID);
    }
}
