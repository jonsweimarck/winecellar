package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.example.winecellar.domain.UserToken.Purpose;

/**
 * Öppen självregistrering (WINE-11, se ADR 0013) med e-postverifiering
 * (WINE-59, se ADR 0026): användarnamnet måste vara en e-postadress, kontot
 * skapas OVERIFIERAT och kan inte logga in förrän användaren klickat på
 * länken i verifieringsmailet. Unikhetskontrollen sitter här (skiftlägesokänslig
 * - användarnamnet normaliseras till gemener), inte i domänobjektet User.
 *
 * <p>Lösenordet är bundet till VERIFIERINGSTOKENET, inte till kontoraden: varje
 * registrering av en overifierad adress utfärdar ett eget token som bär sin
 * lösenordshash, och den som klickar på ett token får det lösenord som hör till
 * just det. Kontoraden skrivs aldrig över före verifiering - bara den som
 * kontrollerar brevlådan kan aktivera något lösenord.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    static final int MAX_VERIFICATION_MAILS_PER_ADDRESS = 3;
    static final Duration VERIFICATION_MAIL_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AccountWriter accountWriter;
    private final MailSender mailSender;
    private final Clock clock;
    private final String baseUrl;
    /** EN kvot per adress för verifieringsmail, gemensam för registrering och "skicka ny länk". */
    private final RequestRateLimiter verificationMailLimiter;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               TokenService tokenService, AccountWriter accountWriter, MailSender mailSender,
                               Clock clock, @Value("${winecellar.base-url}") String baseUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.accountWriter = accountWriter;
        this.mailSender = mailSender;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.verificationMailLimiter = new RequestRateLimiter(
                MAX_VERIFICATION_MAILS_PER_ADDRESS, VERIFICATION_MAIL_WINDOW, clock);
    }

    public RegistrationResult register(String username, String password) {
        Optional<String> email = EmailAddress.normalize(username);
        if (email.isEmpty()) {
            return new RegistrationResult.InvalidEmail();
        }
        Optional<User> existing = userRepository.findByUsername(email.get());
        if (existing.isPresent()) {
            if (existing.get().emailVerified()) {
                return new RegistrationResult.UsernameTaken();
            }
            // Overifierad adress (kanske förhandsregistrerad av någon annan): kontoraden rörs INTE
            // och inga tidigare tokens revokeras. Ett ytterligare token med den nya lösenordshashen
            // utfärdas och mailas, så brevlådans ägare avgör vilket lösenord som aktiveras. Är
            // mailkvoten slut ändras ingenting (inget nytt token); svaret är ändå detsamma.
            if (verificationMailLimiter.tryAcquire(email.get())) {
                String token = tokenService.issueVerification(existing.get().id(), passwordEncoder.encode(password));
                sendVerificationMail(existing.get().username(), token);
            }
            return new RegistrationResult.Registered(existing.get());
        }
        // Senaste login = skapad vid registrering (ingen inloggningshändelse publiceras).
        Instant now = clock.instant();
        String hash = passwordEncoder.encode(password);
        User user;
        try {
            user = userRepository.save(new User(null, email.get(), hash, now, 1, false, false, now, false));
        } catch (DataIntegrityViolationException e) {
            // Samtidig förstagångsregistrering av samma adress (unikt användarnamn): behandla som upptaget.
            log.warn("Samtidig registrering av samma adress - behandlas som upptagen.");
            return new RegistrationResult.UsernameTaken();
        }
        if (verificationMailLimiter.tryAcquire(email.get())) {
            sendVerificationMail(user.username(), tokenService.issueVerification(user.id(), hash));
        }
        return new RegistrationResult.Registered(user);
    }

    /** Kontrollerar ett verifieringstoken utan att förbruka det (för sidan som visar bekräfta-knappen). */
    public TokenOutcome checkVerificationToken(String rawToken) {
        return tokenService.check(Purpose.EMAIL_VERIFICATION, rawToken).outcome();
    }

    /**
     * Aktiverar kontot om tokenet är giltigt, med DET tokenets lösenord. Atomärt (AccountWriter);
     * övriga verifieringstokens raderas. En andra användning ger INVALID.
     */
    public TokenOutcome verifyEmail(String rawToken) {
        TokenService.Redeemed redeemed = tokenService.check(Purpose.EMAIL_VERIFICATION, rawToken);
        if (redeemed.outcome() != TokenOutcome.SUCCESS) {
            return redeemed.outcome();
        }
        return accountWriter.activate(redeemed.token()) ? TokenOutcome.SUCCESS : TokenOutcome.INVALID;
    }

    /**
     * Begär en ny verifieringslänk (de gamla slutar fungera; den nya bär samma väntande
     * lösenord som den senast utfärdade). Avslöjar aldrig om adressen finns eller redan är
     * verifierad - anroparen visar alltid samma neutrala svar. Rate-limitad per adress
     * (gemensamt med registrering); är kvoten slut ändras ingenting.
     *
     * Svarstiden kan skilja mellan en känd overifierad adress (databasskrivning + mail) och en
     * okänd (bara en hash) - medvetet accepterat, se ADR 0026.
     */
    public void resendVerification(String username) {
        Optional<String> email = EmailAddress.normalize(username);
        if (email.isEmpty()) {
            return;
        }
        Optional<User> user = userRepository.findByUsername(email.get()).filter(u -> !u.emailVerified());
        if (user.isEmpty()) {
            // Okända/redan verifierade adresser registreras aldrig i begränsarens karta.
            TokenHasher.hash(TokenHasher.generate());
            return;
        }
        // Kvoten förbrukas först när ett mail faktiskt ska skickas.
        if (verificationMailLimiter.tryAcquire(email.get())) {
            sendVerificationMail(user.get().username(), tokenService.reissueVerification(user.get().id()));
        }
    }

    private void sendVerificationMail(String to, String token) {
        mailSender.send(to, "Verifiera din e-postadress - Vinkällaren",
                "Välkommen till Vinkällaren!\n\n"
                        + "Bekräfta din e-postadress genom att öppna länken nedan. Länken gäller i 24 timmar "
                        + "och kan bara användas en gång.\n\n"
                        + baseUrl + "/verifiera?token=" + token + "\n\n"
                        + "Om du inte har registrerat dig kan du bortse från det här mailet.\n");
    }
}
