package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.UserToken.Purpose;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Öppen självregistrering (WINE-11, se ADR 0013) med e-postverifiering
 * (WINE-59, se ADR 0026): användarnamnet måste vara en e-postadress, kontot
 * skapas OVERIFIERAT och kan inte logga in förrän användaren klickat på
 * länken i verifieringsmailet. Unikhetskontrollen sitter här (skiftlägesokänslig
 * - användarnamnet normaliseras till gemener), inte i domänobjektet User.
 */
@Service
public class RegistrationService {

    static final int MAX_VERIFICATION_MAILS_PER_ADDRESS = 3;
    static final Duration VERIFICATION_MAIL_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final MailSender mailSender;
    private final Clock clock;
    private final String baseUrl;
    private final RequestRateLimiter resendLimiter;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               TokenService tokenService, MailSender mailSender, Clock clock,
                               @Value("${winecellar.base-url}") String baseUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.mailSender = mailSender;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.resendLimiter = new RequestRateLimiter(
                MAX_VERIFICATION_MAILS_PER_ADDRESS, VERIFICATION_MAIL_WINDOW, clock);
    }

    public RegistrationResult register(String username, String password) {
        Optional<String> email = EmailAddress.normalize(username);
        if (email.isEmpty()) {
            return new RegistrationResult.InvalidEmail();
        }
        if (userRepository.findByUsername(email.get()).isPresent()) {
            return new RegistrationResult.UsernameTaken();
        }
        // Senaste login = skapad vid registrering (ingen inloggningshändelse publiceras).
        Instant now = clock.instant();
        User user = userRepository.save(
                new User(null, email.get(), passwordEncoder.encode(password), now, 1, false, false, now, false));
        sendVerificationMail(user);
        return new RegistrationResult.Registered(user);
    }

    /** Kontrollerar ett verifieringstoken utan att förbruka det (för sidan som visar bekräfta-knappen). */
    public TokenOutcome checkVerificationToken(String rawToken) {
        return tokenService.check(Purpose.EMAIL_VERIFICATION, rawToken).outcome();
    }

    /** Aktiverar kontot om tokenet är giltigt. Tokenet förbrukas - en andra användning ger INVALID. */
    public TokenOutcome verifyEmail(String rawToken) {
        TokenService.Redeemed redeemed = tokenService.check(Purpose.EMAIL_VERIFICATION, rawToken);
        if (redeemed.outcome() != TokenOutcome.SUCCESS) {
            return redeemed.outcome();
        }
        tokenService.consume(redeemed.token());
        userRepository.findById(redeemed.token().userId())
                .ifPresent(user -> userRepository.save(user.withEmailVerified(true)));
        return TokenOutcome.SUCCESS;
    }

    /**
     * Begär en ny verifieringslänk (de gamla slutar fungera). Avslöjar aldrig
     * om adressen finns eller redan är verifierad - anroparen visar alltid
     * samma neutrala svar. Rate-limitad per adress.
     */
    public void resendVerification(String username) {
        Optional<String> email = EmailAddress.normalize(username);
        if (email.isEmpty()) {
            return;
        }
        if (!resendLimiter.tryAcquire(email.get())) {
            return;
        }
        Optional<User> user = userRepository.findByUsername(email.get()).filter(u -> !u.emailVerified());
        if (user.isPresent()) {
            sendVerificationMail(user.get());
        } else {
            // Likartat arbete som för en känd adress (se PasswordResetService).
            TokenHasher.hash(TokenHasher.generate());
        }
    }

    private void sendVerificationMail(User user) {
        String token = tokenService.issue(user.id(), Purpose.EMAIL_VERIFICATION);
        mailSender.send(user.username(), "Verifiera din e-postadress - Vinkällaren",
                "Välkommen till Vinkällaren!\n\n"
                        + "Bekräfta din e-postadress genom att öppna länken nedan. Länken gäller i 24 timmar "
                        + "och kan bara användas en gång.\n\n"
                        + baseUrl + "/verifiera?token=" + token + "\n\n"
                        + "Om du inte har registrerat dig kan du bortse från det här mailet.\n");
    }
}
