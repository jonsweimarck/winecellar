package com.example.winecellar.acceptance;

import com.example.winecellar.application.TokenHasher;
import com.example.winecellar.application.UserTokenRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;
import com.example.winecellar.infrastructure.JpaUserRepository;
import com.example.winecellar.support.MutableClock;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.Och;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Städning av utgångna tokens och övergivna overifierade konton vid inloggning (WINE-65). Loggar in
 * genom den RIKTIGA säkerhetskedjan så att den riktiga händelselyssnaren körs. Användare och tokens
 * skapas direkt (med bakdaterad tid), tiden hämtas från samma {@link MutableClock} som städningen.
 * Det enda delade stegtillståndet är senaste inloggningsresultatet, så stegen ligger i en egen klass.
 * Kontot "anna@example.com" skapas av det befintliga steget i RegistrationSteps (Bakgrund).
 */
public class StaleAccountCleanupSteps {

    private static final String TRIGGER = "stale_cleanup_fail_trigger";

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private UserTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private MvcResult lastLogin;

    @Before
    public void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    /** Tar bort den provocerade felkällan även om scenariot självt föll. */
    @After
    public void dropFailureTrigger() {
        jdbcTemplate.execute("drop trigger if exists " + TRIGGER + " on user_tokens");
        jdbcTemplate.execute("drop function if exists " + TRIGGER + "()");
    }

    @Givet("ett overifierat konto {string} skapat för {int} dagar sedan")
    public void ettOverifieratKonto(String username, int days) {
        createUser(username, days, false);
    }

    @Givet("ett verifierat konto {string} skapat för {int} dagar sedan")
    public void ettVerifieratKonto(String username, int days) {
        createUser(username, days, true);
    }

    @Och("kontot {string} har ett utgånget verifieringstoken")
    public void kontotHarUtgångetToken(String username) {
        createToken(username, clock.instant().minus(Duration.ofMinutes(5)));
    }

    @Och("kontot {string} har ett giltigt verifieringstoken")
    public void kontotHarGiltigtToken(String username) {
        createToken(username, clock.instant().plus(Duration.ofHours(12)));
    }

    /**
     * Ett RIKTIGT fel i databasen (inte en mock): en trigger som avvisar varje radering i user_tokens,
     * så att städningens första åtgärd felar. Droppas i {@link #dropFailureTrigger()}.
     */
    @Givet("att städningen kommer att misslyckas")
    public void attStädningenMisslyckas() {
        jdbcTemplate.execute("create or replace function " + TRIGGER + "() returns trigger as $$ "
                + "begin raise exception 'simulerat fel i stadningen'; end; $$ language plpgsql");
        jdbcTemplate.execute("create trigger " + TRIGGER + " before delete on user_tokens "
                + "for each row execute function " + TRIGGER + "()");
    }

    @När("jag loggar in som {string} med lösenordet {string}")
    public void jagLoggarIn(String username, String password) throws Exception {
        lastLogin = mockMvc.perform(post("/login").param("username", username).param("password", password)
                .with(csrf())).andReturn();
    }

    @Så("lyckas inloggningen")
    public void lyckasInloggningen() {
        assertThat(lastLogin.getResponse().getRedirectedUrl()).isEqualTo("/");
    }

    @Och("finns kontot {string} kvar")
    public void finnsKontoKvar(String username) {
        assertThat(userRepository.findByUsername(username)).isPresent();
    }

    @Och("finns kontot {string} inte längre")
    public void finnsKontoInteLängre(String username) {
        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    @Och("har kontot {string} inga verifieringstokens")
    public void harIngaTokens(String username) {
        assertThat(tokenCountFor(username)).isZero();
        // Ingen FK mot users: kontrollera också att inga övergivna tokens blivit kvar utan konto
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user_tokens where user_id not in (select id from users)", Integer.class))
                .isZero();
    }

    @Och("har kontot {string} ett verifieringstoken")
    public void harEttToken(String username) {
        assertThat(tokenCountFor(username)).isEqualTo(1);
    }

    private void createUser(String username, int daysAgo, boolean verified) {
        Instant created = clock.instant().minus(Duration.ofDays(daysAgo));
        userRepository.save(new User(null, username, passwordEncoder.encode("hemligt123"), created, 1, false, false,
                created, verified));
    }

    private void createToken(String username, Instant expiresAt) {
        User user = userRepository.findByUsername(username).orElseThrow();
        tokenRepository.save(new UserToken(null, user.id(), Purpose.EMAIL_VERIFICATION,
                TokenHasher.hash(TokenHasher.generate()), expiresAt));
    }

    /** Räknar via SQL på användarnamnet, så att det även fungerar efter att kontot raderats. */
    private int tokenCountFor(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_tokens t join users u on u.id = t.user_id where u.username = ?",
                Integer.class, username);
        return count == null ? 0 : count;
    }
}
