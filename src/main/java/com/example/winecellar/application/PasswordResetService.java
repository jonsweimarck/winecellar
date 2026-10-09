package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.UserToken.Purpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Glömt lösenord (WINE-59, se ADR 0026). En begäran mailar en länk (giltig 1 h,
 * engångsbruk) bara om adressen tillhör ett VERIFIERAT konto - men anroparen
 * visar alltid samma neutrala svar, och mailutskicket är rate-limitat per
 * adress oavsett om adressen finns. Ett lösenordsbyte avslutar användarens
 * pågående sessioner; hash-baserad remember-me faller av sig själv eftersom
 * dess signatur bygger på lösenordshashen.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    static final int MAX_RESET_MAILS_PER_ADDRESS = 3;
    static final Duration RESET_MAIL_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final MailSender mailSender;
    private final SessionTerminator sessionTerminator;
    private final String baseUrl;
    private final RequestRateLimiter limiter;

    public PasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                TokenService tokenService, MailSender mailSender,
                                SessionTerminator sessionTerminator, Clock clock,
                                @Value("${winecellar.base-url}") String baseUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.mailSender = mailSender;
        this.sessionTerminator = sessionTerminator;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.limiter = new RequestRateLimiter(MAX_RESET_MAILS_PER_ADDRESS, RESET_MAIL_WINDOW, clock);
    }

    /**
     * Returnerar inget - ska aldrig avslöja om adressen finns. Okänd, oväntad
     * eller overifierad adress, och en nådd gräns, ger samma tysta utfall.
     */
    public void requestReset(String username) {
        Optional<String> email = EmailAddress.normalize(username);
        if (email.isEmpty()) {
            return;
        }
        Optional<User> user = userRepository.findByUsername(email.get()).filter(User::emailVerified);
        if (user.isEmpty()) {
            // Likartat arbete (slump + hash) som för en känd adress, så att svarstiden
            // inte avslöjar skillnaden. Själva utskicket är asynkront i SMTP-adaptern.
            TokenHasher.hash(TokenHasher.generate());
            return;
        }
        // Kvoten förbrukas först när ett mail faktiskt ska skickas (okända adresser rör aldrig kartan).
        if (!limiter.tryAcquire(email.get())) {
            return;
        }
        String token;
        try {
            token = tokenService.issue(user.get().id(), Purpose.PASSWORD_RESET);
        } catch (DataIntegrityViolationException e) {
            // Annan samtidig begäran hann före (unikt index) - tyst, samma neutrala svar.
            log.warn("Återställningstoken kunde inte utfärdas (samtidig begäran) för användare {}.", user.get().id());
            return;
        }
        mailSender.send(user.get().username(), "Återställ ditt lösenord - Vinkällaren",
                "Någon (förhoppningsvis du) har begärt att återställa lösenordet för ditt konto i Vinkällaren.\n\n"
                        + "Öppna länken nedan för att välja ett nytt lösenord. Länken gäller i 1 timme och kan "
                        + "bara användas en gång.\n\n"
                        + baseUrl + "/aterstall-losenord?token=" + token + "\n\n"
                        + "Om du inte har begärt det här kan du bortse från mailet - ditt lösenord ändras inte.\n");
    }

    /** Kontrollerar länken utan att förbruka den (för att visa formuläret eller ett felmeddelande). */
    public TokenOutcome checkToken(String rawToken) {
        return tokenService.check(Purpose.PASSWORD_RESET, rawToken).outcome();
    }

    /** Byter lösenord om länken är giltig. Förbrukar länken och avslutar användarens sessioner. */
    public TokenOutcome resetPassword(String rawToken, String newPassword) {
        TokenService.Redeemed redeemed = tokenService.check(Purpose.PASSWORD_RESET, rawToken);
        if (redeemed.outcome() != TokenOutcome.SUCCESS) {
            return redeemed.outcome();
        }
        Optional<User> user = userRepository.findById(redeemed.token().userId());
        if (!tokenService.consume(redeemed.token())) {
            return TokenOutcome.INVALID;
        }
        if (user.isEmpty()) {
            return TokenOutcome.INVALID;
        }
        userRepository.save(user.get().withHashedPassword(passwordEncoder.encode(newPassword)));
        tokenService.revokeAll(user.get().id(), Purpose.PASSWORD_RESET);
        sessionTerminator.terminateSessionsOf(user.get().username());
        return TokenOutcome.SUCCESS;
    }
}
