package com.example.winecellar.application;

import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Utfärdar och löser in engångstokens (WINE-59, se ADR 0026). Verifieringslänk
 * gäller 24 h, återställningslänk 1 h. En ny utfärdning ogiltigförklarar
 * tidigare tokens av samma slag för samma användare. Ett inlöst token raderas
 * atomärt - det är raderingen som är engångsgrinden.
 */
@Service
public class TokenService {

    public static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    public static final Duration RESET_TTL = Duration.ofHours(1);
    public static final int MAX_VERIFICATION_TOKENS = 3;

    public record Redeemed(TokenOutcome outcome, UserToken token) {
    }

    private final UserTokenRepository tokenRepository;
    private final Clock clock;

    public TokenService(UserTokenRepository tokenRepository, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    /**
     * @return det RÅA tokenet - lagras aldrig, skickas bara i mailet. Radering av
     * tidigare och sparande av nytt sker i EN transaktion, och databasen har
     * ett unikt index på (användare, slag) som sista skydd: två samtidiga
     * anrop kan aldrig lämna två giltiga tokens (den som förlorar racet får ett fel).
     */
    @Transactional
    public String issue(UserId userId, Purpose purpose) {
        if (purpose == Purpose.EMAIL_VERIFICATION) {
            return issueVerification(userId, null);
        }
        tokenRepository.deleteByUserAndPurpose(userId, purpose);
        return save(userId, purpose, null);
    }

    /**
     * Utfärdar ett ytterligare verifieringstoken som bär sin egen väntande lösenordshash
     * (null = behåll kontots). Äldre tokens revokeras INTE; antalet håller sig under
     * {@link #MAX_VERIFICATION_TOKENS} genom att de äldsta raderas. Se ADR 0026.
     */
    @Transactional
    public String issueVerification(UserId userId, String pendingPasswordHash) {
        String raw = save(userId, Purpose.EMAIL_VERIFICATION, pendingPasswordHash);
        List<UserToken> all = tokenRepository.findByUserAndPurpose(userId, Purpose.EMAIL_VERIFICATION);
        for (int i = 0; i < all.size() - MAX_VERIFICATION_TOKENS; i++) {
            tokenRepository.deleteById(all.get(i).id());
        }
        return raw;
    }

    /**
     * "Skicka ny länk": ersätter användarens verifieringstokens med ETT nytt som bär samma
     * väntande lösenordshash som det senast utfärdade (eller null om inget finns kvar).
     */
    @Transactional
    public String reissueVerification(UserId userId) {
        List<UserToken> all = tokenRepository.findByUserAndPurpose(userId, Purpose.EMAIL_VERIFICATION);
        String pending = all.isEmpty() ? null : all.get(all.size() - 1).pendingPasswordHash();
        tokenRepository.deleteByUserAndPurpose(userId, Purpose.EMAIL_VERIFICATION);
        return save(userId, Purpose.EMAIL_VERIFICATION, pending);
    }

    private String save(UserId userId, Purpose purpose, String pendingPasswordHash) {
        String raw = TokenHasher.generate();
        Duration ttl = purpose == Purpose.EMAIL_VERIFICATION ? VERIFICATION_TTL : RESET_TTL;
        tokenRepository.save(new UserToken(null, userId, purpose, TokenHasher.hash(raw),
                clock.instant().plus(ttl), pendingPasswordHash));
        return raw;
    }

    /** Slår upp utan att förbruka. Ett utgånget token raderas och rapporteras som EXPIRED. */
    public Redeemed check(Purpose purpose, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return new Redeemed(TokenOutcome.INVALID, null);
        }
        String raw = rawToken.trim();
        Optional<UserToken> found = tokenRepository.findByHash(purpose, TokenHasher.hash(raw))
                .filter(token -> TokenHasher.matches(raw, token.tokenHash()));
        if (found.isEmpty()) {
            return new Redeemed(TokenOutcome.INVALID, null);
        }
        UserToken token = found.get();
        if (!clock.instant().isBefore(token.expiresAt())) {
            tokenRepository.deleteById(token.id());
            return new Redeemed(TokenOutcome.EXPIRED, null);
        }
        return new Redeemed(TokenOutcome.SUCCESS, token);
    }

    /** Förbrukar tokenet atomärt (engångsbruk). @return false om någon annan hann före. */
    public boolean consume(UserToken token) {
        return tokenRepository.deleteById(token.id());
    }

    public void revokeAll(UserId userId, Purpose purpose) {
        tokenRepository.deleteByUserAndPurpose(userId, purpose);
    }
}
