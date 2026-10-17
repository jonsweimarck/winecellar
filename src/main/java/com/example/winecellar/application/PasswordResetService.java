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
 * adress oavsett om adressen finns, samt av det globala mailtaket (WINE-65). Ett
 * lösenordsbyte avslutar användarens pågående sessioner; hash-baserad remember-me
 * faller av sig själv eftersom dess signatur bygger på lösenordshashen.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    static final int MAX_RESET_MAILS_PER_ADDRESS = 3;
    static final Duration RESET_MAIL_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AccountWriter accountWriter;
    private final MailSender mailSender;
    private final MailBudget mailBudget;
    private final SessionTerminator sessionTerminator;
    private final String baseUrl;
    private final RequestRateLimiter limiter;

    public PasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                TokenService tokenService, AccountWriter accountWriter, MailSender mailSender,
                                @org.springframework.beans.factory.annotation.Qualifier("resetMailBudget") MailBudget mailBudget, SessionTerminator sessionTerminator, Clock clock,
                                @Value("${winecellar.base-url}") String baseUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.accountWriter = accountWriter;
        this.mailSender = mailSender;
        this.mailBudget = mailBudget;
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
        // Kvoterna tas först när ett mail faktiskt ska skickas (okända adresser rör aldrig kartan).
        String address = email.get();
        if (!limiter.tryAcquire(address)) {
            return;
        }
        if (!mailBudget.tryAcquire()) {
            limiter.release(address);
            return;
        }
        // Båda platserna återlämnas om inget mail faktiskt lämnas över till avsändaren, oavsett orsak.
        boolean handedOver = false;
        try {
            String token;
            try {
                token = tokenService.issue(user.get().id(), Purpose.PASSWORD_RESET);
            } catch (DataIntegrityViolationException e) {
                // Annan samtidig begäran hann före (unikt index) - tyst, samma neutrala svar.
                log.warn("Återställningstoken kunde inte utfärdas (samtidig begäran) för användare {}.",
                        user.get().id());
                return;
            }
            mailSender.send(user.get().username(), "Återställ ditt lösenord - Vinkällaren",
                    "Någon (förhoppningsvis du) har begärt att återställa lösenordet för ditt konto i Vinkällaren.\n\n"
                            + "Öppna länken nedan för att välja ett nytt lösenord. Länken gäller i 1 timme och kan "
                            + "bara användas en gång.\n\n"
                            + baseUrl + "/aterstall-losenord?token=" + token + "\n\n"
                            + "Om du inte har begärt det här kan du bortse från mailet - ditt lösenord ändras inte.\n",
                    // Mailet gick inte att skicka: användaren ska inte ha förlorat en plats i sin egen kvot.
                    () -> limiter.release(address));
            handedOver = true;
        } finally {
            if (!handedOver) {
                limiter.release(address);
                mailBudget.release();
            }
        }
    }

    /** Kontrollerar länken utan att förbruka den (för att visa formuläret eller ett felmeddelande). */
    public TokenOutcome checkToken(String rawToken) {
        return tokenService.check(Purpose.PASSWORD_RESET, rawToken).outcome();
    }

    /**
     * Byter lösenord om länken är giltig. Förbrukning av länken och själva bytet sker atomärt i
     * {@link AccountWriter} (WINE-65): felar något rullas allt tillbaka. Sessionerna avslutas först efter
     * lyckad commit.
     */
    public TokenOutcome resetPassword(String rawToken, String newPassword) {
        TokenService.Redeemed redeemed = tokenService.check(Purpose.PASSWORD_RESET, rawToken);
        if (redeemed.outcome() != TokenOutcome.SUCCESS) {
            return redeemed.outcome();
        }
        Optional<User> user = userRepository.findById(redeemed.token().userId());
        if (user.isEmpty()) {
            return TokenOutcome.INVALID;
        }
        if (!accountWriter.resetPassword(redeemed.token(), passwordEncoder.encode(newPassword))) {
            return TokenOutcome.INVALID;
        }
        sessionTerminator.terminateSessionsOf(user.get().username());
        return TokenOutcome.SUCCESS;
    }
}
