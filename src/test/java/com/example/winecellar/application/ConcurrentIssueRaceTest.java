package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.infrastructure.InMemoryUserRepository;
import com.example.winecellar.support.FakeMailSender;
import com.example.winecellar.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** WINE-59: förloraren i ett samtidigt token-race ger inget fel utan det neutrala utfallet. */
class ConcurrentIssueRaceTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final FakeMailSender mail = new FakeMailSender();
    private final MutableClock clock = new MutableClock();
    private final TokenService losingTokens = mock(TokenService.class);

    ConcurrentIssueRaceTest() {
        when(losingTokens.issue(any(), any())).thenThrow(new DataIntegrityViolationException("unikt index"));
    }

    @Test
    void samtidigFörstagångsregistreringSkaBehandlasSomUpptagetUtanFel() {
        UserRepository racing = mock(UserRepository.class);
        when(racing.findByUsername(any())).thenReturn(java.util.Optional.empty());
        when(racing.save(any())).thenThrow(new DataIntegrityViolationException("unikt användarnamn"));
        RegistrationService service = new RegistrationService(racing, encoder, losingTokens,
                new AccountWriter(racing, losingTokens), mail, clock, "http://x");

        assertThat(service.register("anna@example.com"))
                .isInstanceOf(RegistrationResult.UsernameTaken.class);
        assertThat(mail.all()).isEmpty();
    }

    @Test
    void ÅterställningSkaInteKraschaOchInteSkickaMail() {
        Instant now = Instant.now();
        users.save(new User(null, "anna@example.com", "h", now, 1, false, false, now, true));
        PasswordResetService service = new PasswordResetService(users, encoder, losingTokens, mail,
                mock(SessionTerminator.class), clock, "http://x");
        assertThatCode(() -> service.requestReset("anna@example.com")).doesNotThrowAnyException();
        assertThat(mail.all()).isEmpty();
    }

    @Test
    void omregistreringOchNyLänkSkaInteKraschaOchInteSkickaMailNärTokenRaceFörloras() {
        Instant now = Instant.now();
        users.save(new User(null, "anna@example.com", "h", now, 1, false, false, now, false));
        RegistrationService service = new RegistrationService(users, encoder, losingTokens,
                new AccountWriter(users, losingTokens), mail, clock, "http://x");

        assertThat(service.register("anna@example.com")).isInstanceOf(RegistrationResult.Registered.class);
        assertThatCode(() -> service.resendVerification("anna@example.com")).doesNotThrowAnyException();
        assertThat(mail.all()).isEmpty();
    }
}
