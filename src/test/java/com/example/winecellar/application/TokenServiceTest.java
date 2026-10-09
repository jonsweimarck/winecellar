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
