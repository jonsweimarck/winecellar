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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void platshållarhashenSkaHaSammaPrefixOchKostnadsfaktorSomAppensRiktigaHashar() {
        String real = encoder.encode("nagot");
        String placeholderHeader = RegistrationService.UNUSABLE_PASSWORD_HASH.substring(0, 15);
        assertThat(placeholderHeader).isEqualTo("{bcrypt}$2a$10$");
        assertThat(real).startsWith(placeholderHeader);
    }

    @Test
    void skaLämnaTillbakaBådaPlatsernaNärUtfärdandetFelarMedEttAnnatUndantagÄnRace() {
        MailBudget budget = new MailBudget(1, 100, clock);
        TokenService failing = org.mockito.Mockito.mock(TokenService.class);
        org.mockito.Mockito.when(failing.issue(any(), any())).thenThrow(new IllegalStateException("db nere"));
        RegistrationService service = new RegistrationService(users, encoder, failing,
                new AccountWriter(users, failing), mail, budget, clock, "http://x");
        users.save(new User(null, "anna@example.com", "h", clock.instant(), 1, false, false, clock.instant(),
                false));

        // Fler försök än per-adress-kvoten (3): lyckas varje gång bara om platsen lämnades tillbaka
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.resendVerification("anna@example.com"))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(budget.tryAcquire()).isTrue();
        assertThat(mail.all()).isEmpty();
    }

    @Test
    void skaLämnaTillbakaDenGlobalaPlatsenNärKontoSparandetFelarMedEttAnnatUndantagÄnDubblett() {
        MailBudget budget = new MailBudget(1, 100, clock);
        UserRepository failingUsers = org.mockito.Mockito.mock(UserRepository.class);
        org.mockito.Mockito.when(failingUsers.findByUsername(any())).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(failingUsers.save(any())).thenThrow(new IllegalStateException("db nere"));
        RegistrationService service = new RegistrationService(failingUsers, encoder, tokenService,
                new AccountWriter(failingUsers, tokenService), mail, budget, clock, "http://x");

        assertThatThrownBy(() -> service.register("anna@example.com")).isInstanceOf(IllegalStateException.class);

        assertThat(budget.tryAcquire()).isTrue();
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
