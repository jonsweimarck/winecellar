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
import java.time.Instant;
import java.util.Optional;

/**
 * Öppen självregistrering (WINE-11, se ADR 0013) med e-postverifiering
 * (WINE-59, se ADR 0026): användarnamnet måste vara en e-postadress. Registreringen
 * frågar BARA efter adressen - kontot skapas overifierat med ett oanvändbart,
 * slumpmässigt lösenord (en hash av 256 bitar slump som aldrig sparas någon annanstans)
 * och användaren väljer sitt riktiga lösenord först när hen öppnar länken i mailet.
 * Därför kan ingen som bara känner till en adress ta över ett konto: det finns inget
 * lösenord att känna till, och bara den som kontrollerar brevlådan kan välja ett.
 * Unikhetskontrollen sitter här (skiftlägesokänslig - användarnamnet normaliseras till
 * gemener), inte i domänobjektet User.
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

    public RegistrationResult register(String username) {
        Optional<String> email = EmailAddress.normalize(username);
        if (email.isEmpty()) {
            return new RegistrationResult.InvalidEmail();
        }
        Optional<User> existing = userRepository.findByUsername(email.get());
        if (existing.isPresent()) {
            if (existing.get().emailVerified()) {
                return new RegistrationResult.UsernameTaken();
            }
            // Omregistrering av en overifierad adress = "skicka ny länk" (samma kvot, samma neutrala svar).
            sendNewLink(existing.get());
            return new RegistrationResult.Registered(existing.get());
        }
        // Senaste login = skapad vid registrering (ingen inloggningshändelse publiceras).
        Instant now = clock.instant();
        User user;
        try {
            user = userRepository.save(new User(null, email.get(), unusablePasswordHash(), now, 1, false, false,
                    now, false));
        } catch (DataIntegrityViolationException e) {
            // Samtidig förstagångsregistrering av samma adress (unikt användarnamn): behandla som upptaget.
            log.warn("Samtidig registrering av samma adress - behandlas som upptagen.");
            return new RegistrationResult.UsernameTaken();
        }
        sendNewLink(user);
        return new RegistrationResult.Registered(user);
    }

    /** Kontrollerar ett verifieringstoken utan att förbruka det (för sidan som visar lösenordsformuläret). */
    public TokenOutcome checkVerificationToken(String rawToken) {
        return tokenService.check(Purpose.EMAIL_VERIFICATION, rawToken).outcome();
    }

    /**
     * Aktiverar kontot med det valda lösenordet om tokenet är giltigt. Atomärt (AccountWriter):
     * tokenet förbrukas, lösenordet sätts och kontot markeras verifierat i en transaktion. En andra
     * användning ger INVALID. Anroparen har redan kontrollerat att lösenordet inte är tomt och
     * matchar bekräftelsen (samma regler som "glömt lösenord").
     */
    public TokenOutcome activateAccount(String rawToken, String password) {
        TokenService.Redeemed redeemed = tokenService.check(Purpose.EMAIL_VERIFICATION, rawToken);
        if (redeemed.outcome() != TokenOutcome.SUCCESS) {
            return redeemed.outcome();
        }
        return accountWriter.activate(redeemed.token(), passwordEncoder.encode(password))
                ? TokenOutcome.SUCCESS : TokenOutcome.INVALID;
    }

    /**
     * Begär en ny verifieringslänk (den gamla slutar fungera). Avslöjar aldrig om adressen finns
     * eller redan är verifierad - anroparen visar alltid samma neutrala svar. Rate-limitad per
     * adress (gemensamt med registrering); är kvoten slut ändras ingenting.
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
        sendNewLink(user.get());
    }

    /**
     * Ersätter användarens verifieringstoken med ett nytt och mailar det - under den gemensamma
     * kvoten. Är kvoten slut ändras ingenting. Kvoten förbrukas först när utfärdningen lyckats.
     * Förloraren i ett samtidigt race på det unika indexet loggas på WARN och besvaras neutralt.
     */
    private void sendNewLink(User user) {
        if (!verificationMailLimiter.hasCapacity(user.username())) {
            return;
        }
        String token;
        try {
            token = tokenService.issue(user.id(), Purpose.EMAIL_VERIFICATION);
        } catch (DataIntegrityViolationException e) {
            log.warn("Verifieringstoken kunde inte utfärdas (samtidig begäran) för användare {}.", user.id());
            return;
        }
        verificationMailLimiter.tryAcquire(user.username());
        mailSender.send(user.username(), "Verifiera din e-postadress - Vinkällaren",
                "Välkommen till Vinkällaren!\n\n"
                        + "Öppna länken nedan för att bekräfta din e-postadress och välja ett lösenord. Länken "
                        + "gäller i 24 timmar och kan bara användas en gång.\n\n"
                        + baseUrl + "/verifiera?token=" + token + "\n\n"
                        + "Om du inte har registrerat dig kan du bortse från det här mailet.\n");
    }

    /** En hash av 256 bitar slump som kastas direkt - ingen känner till ett lösenord som matchar den. */
    private String unusablePasswordHash() {
        return passwordEncoder.encode(TokenHasher.generate());
    }
}
