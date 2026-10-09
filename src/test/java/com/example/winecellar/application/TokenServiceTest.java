package com.example.winecellar.application;

import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.UserToken.Purpose;
import com.example.winecellar.infrastructure.InMemoryUserTokenRepository;
import com.example.winecellar.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    private static final UserId USER = new UserId(1L);

    private InMemoryUserTokenRepository repository;
    private MutableClock clock;
    private TokenService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserTokenRepository();
        clock = new MutableClock();
        service = new TokenService(repository, clock);
    }

    @Test
    void skaBaraLagraHashenAvTokenet() {
        String raw = service.issue(USER, Purpose.EMAIL_VERIFICATION);
        assertThat(repository.findByHash(Purpose.EMAIL_VERIFICATION, raw)).isEmpty();
        assertThat(repository.findByHash(Purpose.EMAIL_VERIFICATION, TokenHasher.hash(raw))).isPresent();
    }

    @Test
    void skaBaraLåtaEnAvFleraSamtidigaInlösenLyckas() throws Exception {
        String raw = service.issue(USER, Purpose.PASSWORD_RESET);
        var token = service.check(Purpose.PASSWORD_RESET, raw).token();
        assertThat(service.consume(token)).isTrue();
        assertThat(service.consume(token)).isFalse();

        String raw2 = service.issue(USER, Purpose.PASSWORD_RESET);
        var token2 = service.check(Purpose.PASSWORD_RESET, raw2).token();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.Future<Boolean>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> service.consume(token2)));
            }
            int wins = 0;
            for (var f : results) {
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
    void skaTillåtaFleraVerifieringstokensMedEgenLösenordshashMenHållaAntaletBegränsat() {
        String t1 = service.issueVerification(USER, "hash-1");
        String t2 = service.issueVerification(USER, "hash-2");
        String t3 = service.issueVerification(USER, "hash-3");
        assertThat(service.check(Purpose.EMAIL_VERIFICATION, t1).token().pendingPasswordHash()).isEqualTo("hash-1");
        assertThat(service.check(Purpose.EMAIL_VERIFICATION, t2).token().pendingPasswordHash()).isEqualTo("hash-2");

        String t4 = service.issueVerification(USER, "hash-4");
        // Äldsta (t1) har skjutits ut, övriga gäller fortfarande
        assertThat(service.check(Purpose.EMAIL_VERIFICATION, t1).outcome()).isEqualTo(TokenOutcome.INVALID);
        assertThat(service.check(Purpose.EMAIL_VERIFICATION, t2).outcome()).isEqualTo(TokenOutcome.SUCCESS);
        assertThat(service.check(Purpose.EMAIL_VERIFICATION, t3).outcome()).isEqualTo(TokenOutcome.SUCCESS);
        assertThat(service.check(Purpose.EMAIL_VERIFICATION, t4).outcome()).isEqualTo(TokenOutcome.SUCCESS);
    }

    @Test
    void skaErsättaAllaVerifieringstokensMedEttSomBärSenasteLösenordshashVidNyLänk() {
        String t1 = service.issueVerification(USER, "hash-1");
        String t2 = service.issueVerification(USER, "hash-2");

        String fresh = service.reissueVerification(USER);

        assertThat(service.check(Purpose.EMAIL_VERIFICATION, t1).outcome()).isEqualTo(TokenOutcome.INVALID);
        assertThat(service.check(Purpose.EMAIL_VERIFICATION, t2).outcome()).isEqualTo(TokenOutcome.INVALID);
        assertThat(service.check(Purpose.EMAIL_VERIFICATION, fresh).token().pendingPasswordHash())
                .isEqualTo("hash-2");
    }

    @Test
    void skaGodkännaGiltigtTokenOchAvvisaOkäntOchTomt() {
        String raw = service.issue(USER, Purpose.PASSWORD_RESET);
        assertThat(service.check(Purpose.PASSWORD_RESET, raw).outcome()).isEqualTo(TokenOutcome.SUCCESS);
        assertThat(service.check(Purpose.PASSWORD_RESET, "fel").outcome()).isEqualTo(TokenOutcome.INVALID);
        assertThat(service.check(Purpose.PASSWORD_RESET, null).outcome()).isEqualTo(TokenOutcome.INVALID);
        assertThat(service.check(Purpose.PASSWORD_RESET, " ").outcome()).isEqualTo(TokenOutcome.INVALID);
    }

    @Test
    void skaInteGodkännaEttTokenAvFelSlag() {
        String raw = service.issue(USER, Purpose.EMAIL_VERIFICATION);
        assertThat(service.check(Purpose.PASSWORD_RESET, raw).outcome()).isEqualTo(TokenOutcome.INVALID);
    }

    @Test
    void skaVaraEngångsbruk() {
        String raw = service.issue(USER, Purpose.PASSWORD_RESET);
        TokenService.Redeemed redeemed = service.check(Purpose.PASSWORD_RESET, raw);
        service.consume(redeemed.token());
        assertThat(service.check(Purpose.PASSWORD_RESET, raw).outcome()).isEqualTo(TokenOutcome.INVALID);
    }

    @Test
    void skaGällaIEnTimmeFörÅterställningOch24TimmarFörVerifiering() {
        String reset = service.issue(USER, Purpose.PASSWORD_RESET);
        String verification = service.issue(USER, Purpose.EMAIL_VERIFICATION);

        clock.advance(Duration.ofMinutes(59));
        assertThat(service.check(Purpose.PASSWORD_RESET, reset).outcome()).isEqualTo(TokenOutcome.SUCCESS);
        clock.advance(Duration.ofMinutes(2));
        assertThat(service.check(Purpose.PASSWORD_RESET, reset).outcome()).isEqualTo(TokenOutcome.EXPIRED);

        clock.advance(Duration.ofHours(22));
        assertThat(service.check(Purpose.EMAIL_VERIFICATION, verification).outcome()).isEqualTo(TokenOutcome.SUCCESS);
        clock.advance(Duration.ofHours(2));
        assertThat(service.check(Purpose.EMAIL_VERIFICATION, verification).outcome()).isEqualTo(TokenOutcome.EXPIRED);
    }

    @Test
    void skaOgiltigförklaraTidigareTokensVidNyUtfärdningMenBaraAvSammaSlagOchAnvändare() {
        String first = service.issue(USER, Purpose.PASSWORD_RESET);
        String verification = service.issue(USER, Purpose.EMAIL_VERIFICATION);
        String otherUser = service.issue(new UserId(2L), Purpose.PASSWORD_RESET);

        String second = service.issue(USER, Purpose.PASSWORD_RESET);

        assertThat(service.check(Purpose.PASSWORD_RESET, first).outcome()).isEqualTo(TokenOutcome.INVALID);
        assertThat(service.check(Purpose.PASSWORD_RESET, second).outcome()).isEqualTo(TokenOutcome.SUCCESS);
        assertThat(service.check(Purpose.EMAIL_VERIFICATION, verification).outcome()).isEqualTo(TokenOutcome.SUCCESS);
        assertThat(service.check(Purpose.PASSWORD_RESET, otherUser).outcome()).isEqualTo(TokenOutcome.SUCCESS);
    }
}
