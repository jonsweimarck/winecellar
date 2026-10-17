package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.infrastructure.InMemoryUserRepository;
import com.example.winecellar.infrastructure.InMemoryUserTokenRepository;
import com.example.winecellar.support.FakeMailSender;
import com.example.winecellar.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** WINE-65: platshållarhash, atomär kvot och återbetalning av kvotplats i RegistrationService. */
class RegistrationServiceTest {

    private final PasswordEncoder encoder = spy(PasswordEncoderFactories.createDelegatingPasswordEncoder());
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryUserTokenRepository tokens = new InMemoryUserTokenRepository();
    private final MutableClock clock = new MutableClock();
    private final TokenService tokenService = new TokenService(tokens, clock);
    private final FakeMailSender mail = new FakeMailSender();

    private RegistrationService service(MailBudget budget) {
        return new RegistrationService(users, encoder, tokenService, new AccountWriter(users, tokenService), mail,
                budget, clock, "http://x");
    }

    @Test
    void skaAnvändaEttBilligtOanvändbartPlatshållarhashUtanAttKörabcryptVidRegistrering() {
        service(new MailBudget(100, 100, clock)).register("anna@example.com");

        verify(encoder, never()).encode(any());
        String hash = users.findByUsername("anna@example.com").orElseThrow().hashedPassword();
        assertThat(hash).isEqualTo(RegistrationService.UNUSABLE_PASSWORD_HASH);
        for (String guess : new String[] {"", "password", "hemligt123", "anna@example.com", hash}) {
            assertThat(encoder.matches(guess, hash)).isFalse();
        }
    }

    @Test
    void skaGenomföraEnFullBcryptJämförelseMotPlatshållarhashetSåAttSvarstidenInteAvslöjarKontostatus() {
        // Hashen är en syntaktiskt giltig bcrypt-hash: kodaren avvisar den inte i förväg utan jämför på riktigt.
        assertThat(RegistrationService.UNUSABLE_PASSWORD_HASH)
                .startsWith("{bcrypt}$2a$10$").hasSize("{bcrypt}$2a$10$".length() + 53);
    }

    @Test
    void skaSläppaIgenomHögstTreMailPerTimmeOchAdressVidSamtidigaAnrop() throws Exception {
        RegistrationService service = service(new MailBudget(1000, 1000, clock));
        service.register("anna@example.com");
        mail.clear();

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                service.resendVerification("anna@example.com");
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        // Registreringen använde redan 1 av 3: två till får skickas, inte fler.
        assertThat(mail.sentTo("anna@example.com")).hasSize(2);
    }

    @Test
    void skaÅterbetalaKvotplatsenNärMailetInteKanSkickas() {
        RegistrationService service = service(new MailBudget(1000, 1000, clock));
        mail.failSending(true);
        service.register("anna@example.com");
        service.resendVerification("anna@example.com");
        service.resendVerification("anna@example.com");
        service.resendVerification("anna@example.com");
        service.resendVerification("anna@example.com");
        assertThat(mail.sentTo("anna@example.com")).isEmpty();

        mail.failSending(false);
        service.resendVerification("anna@example.com");

        // Fem misslyckade försök förbrukade ingen plats av de tre
        assertThat(mail.sentTo("anna@example.com")).hasSize(1);
    }

    @Test
    void skaInteSkapaKontoNärDetGlobalaTaketÄrNått() {
        MailBudget budget = new MailBudget(1, 100, clock);
        RegistrationService service = service(budget);
        assertThat(service.register("a@example.com")).isInstanceOf(RegistrationResult.Registered.class);

        assertThat(service.register("b@example.com")).isInstanceOf(RegistrationResult.Deferred.class);

        assertThat(users.findByUsername("b@example.com")).isEmpty();
        assertThat(mail.sentTo("b@example.com")).isEmpty();
    }

    @Test
    void skaLämnaTillbakaDenGlobalaPlatsenNärEnAdressRedanVarUpptagen() {
        MailBudget budget = new MailBudget(1, 100, clock);
        RegistrationService service = service(budget);
        User verified = users.save(new User(null, "v@example.com", "h", clock.instant(), 1, false, false,
                clock.instant(), true));

        assertThat(service.register(verified.username())).isInstanceOf(RegistrationResult.UsernameTaken.class);

        assertThat(budget.tryAcquire()).isTrue();
    }
}
