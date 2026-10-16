package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.UserToken.Purpose;
import com.example.winecellar.infrastructure.InMemoryUserRepository;
import com.example.winecellar.infrastructure.InMemoryUserTokenRepository;
import com.example.winecellar.support.FakeMailSender;
import com.example.winecellar.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** WINE-59: aktivering sätter lösenord + verifierad atomärt, och ändrar aldrig ett redan verifierat konto. */
class AccountWriterTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryUserTokenRepository tokens = new InMemoryUserTokenRepository();
    private final MutableClock clock = new MutableClock();
    private final TokenService tokenService = new TokenService(tokens, clock);
    private final AccountWriter writer = new AccountWriter(users, tokenService);
    private final FakeMailSender mail = new FakeMailSender();
    private final RegistrationService service =
            new RegistrationService(users, encoder, tokenService, writer, mail, clock, "http://x");

    @Test
    void skaSättaDetValdaLösenordetOchVerifieraKontot() {
        service.register("anna@example.com");
        User before = users.findByUsername("anna@example.com").orElseThrow();
        assertThat(before.emailVerified()).isFalse();
        // Ingen känner till ett lösenord som matchar den oanvändbara hashen
        assertThat(encoder.matches("hemligt123", before.hashedPassword())).isFalse();

        String token = mail.sentTo("anna@example.com").get(0).token();
        assertThat(service.activateAccount(token, "hemligt123")).isEqualTo(TokenOutcome.SUCCESS);

        User after = users.findByUsername("anna@example.com").orElseThrow();
        assertThat(after.emailVerified()).isTrue();
        assertThat(encoder.matches("hemligt123", after.hashedPassword())).isTrue();
    }

    @Test
    void skaInteÄndraLösenordetPåEttRedanVerifieratKonto() {
        Instant now = Instant.now();
        String original = encoder.encode("originalLösen1");
        User verified = users.save(new User(null, "anna@example.com", original, now, 1, false, false, now, true));
        String raw = tokenService.issue(verified.id(), Purpose.EMAIL_VERIFICATION);

        assertThat(service.activateAccount(raw, "angripare123")).isEqualTo(TokenOutcome.INVALID);

        assertThat(users.findByUsername("anna@example.com").orElseThrow().hashedPassword()).isEqualTo(original);
    }

    @Test
    void skaGeExaktEnVinnareVidSamtidigDubbelinlösen() throws Exception {
        service.register("anna@example.com");
        String token = mail.sentTo("anna@example.com").get(0).token();
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<TokenOutcome>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String password = "lösen" + i;
                results.add(pool.submit(() -> service.activateAccount(token, password)));
            }
            int winners = 0;
            for (Future<TokenOutcome> f : results) {
                if (f.get() == TokenOutcome.SUCCESS) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void omregistreringAvOverifieradAdressSkaErsättaLänkenUtanAttRöraKontot() {
        service.register("anna@example.com");
        String first = mail.sentTo("anna@example.com").get(0).token();
        String hashBefore = users.findByUsername("anna@example.com").orElseThrow().hashedPassword();

        service.register("anna@example.com");

        assertThat(service.checkVerificationToken(first)).isEqualTo(TokenOutcome.INVALID);
        assertThat(service.checkVerificationToken(mail.sentTo("anna@example.com").get(1).token()))
                .isEqualTo(TokenOutcome.SUCCESS);
        assertThat(users.findByUsername("anna@example.com").orElseThrow().hashedPassword()).isEqualTo(hashBefore);
    }

    @Test
    void tömdKvotSkaInteÄndraNågotOchLämnaSenasteLänkenGiltig() {
        service.register("anna@example.com");
        service.register("anna@example.com");
        service.register("anna@example.com");
        String last = mail.sentTo("anna@example.com").get(2).token();

        service.register("anna@example.com");

        assertThat(mail.sentTo("anna@example.com")).hasSize(3);
        assertThat(service.checkVerificationToken(last)).isEqualTo(TokenOutcome.SUCCESS);
    }
}
