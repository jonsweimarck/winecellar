package com.example.winecellar.application;

import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
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
        tokenRepository.deleteByUserAndPurpose(userId, purpose);
        String raw = TokenHasher.generate();
        Duration ttl = purpose == Purpose.EMAIL_VERIFICATION ? VERIFICATION_TTL : RESET_TTL;
        tokenRepository.save(new UserToken(null, userId, purpose, TokenHasher.hash(raw), clock.instant().plus(ttl)));
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
