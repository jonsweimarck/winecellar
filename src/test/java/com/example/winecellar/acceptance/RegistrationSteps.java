package com.example.winecellar.acceptance;

import com.example.winecellar.application.RegistrationResult;
import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.application.TokenHasher;
import com.example.winecellar.application.TokenOutcome;
import com.example.winecellar.domain.User;
import com.example.winecellar.infrastructure.JpaUserRepository;
import com.example.winecellar.support.FakeMailSender;
import com.example.winecellar.support.MutableClock;
import com.example.winecellar.support.TestAccounts;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.Och;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Registrering med e-postverifiering och återställning av glömt lösenord
 * (WINE-11, WINE-59, se ADR 0013/0026). Körs mot Spring-hanterade bönor, en
 * riktig Postgres (Testcontainers) och den riktiga säkerhetskedjan via
 * MockMvc. Mail fångas av {@link FakeMailSender} och tiden flyttas med
 * {@link MutableClock} (se AcceptanceTestBeans).
 *
 * Alla steg ligger i EN klass (se CLAUDE.md om delade steg). Steget "att ett
 * konto med användarnamnet ... redan finns" återanvänds av andra features och
 * skapar ett redan VERIFIERAT konto.
 */
public class RegistrationSteps {

    private static final String DEFAULT_PASSWORD = "ettLösenord123";

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private TestAccounts testAccounts;

    @Autowired
    private FakeMailSender mailSender;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private RegistrationResult lastRegistration;
    private TokenOutcome lastOutcome;
    private MvcResult lastLogin;
    private MvcResult lastPage;
    private final Map<String, String> firstResetBodies = new HashMap<>();
    private final List<String> responseBodies = new java.util.ArrayList<>();
    private final Map<String, MockHttpSession> sessions = new HashMap<>();

    /**
     * `order = 0` - mittersta steget av en tredelad, klassöverskridande
     * städordning; se {@code PersistenceSteps.raderaAllaViner()}s Javadoc
     * för den fullständiga motiveringen (varför viner måste raderas
     * FÖRE users, och users FÖRE ett nytt testkonto registreras).
     *
     * Klockan flyttas två timmar framåt före varje scenario: rate-limitarna
     * (som lever i minnet i de singleton-tjänster Spring-kontexten delar)
     * räknar då tidigare scenariers begäranden som utgångna, utan att
     * produktionskoden behöver något test-only-nollställningsanrop.
     */
    @Before(order = 0)
    public void reset() {
        jdbcTemplate.update("delete from user_tokens");
        userRepository.deleteAll();
        clock.advance(Duration.ofHours(2));
        mailSender.clear();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    // ---- Registrering -------------------------------------------------

    @Givet("att ett konto med användarnamnet {string} redan finns")
    public void attEttKontoMedAnvändarnamnetRedanFinns(String username) {
        testAccounts.register(username, DEFAULT_PASSWORD);
    }

    @Givet("att jag har registrerat mig med användarnamnet {string} och lösenordet {string}")
    public void attJagHarRegistreratMig(String username, String password) {
        lastRegistration = registrationService.register(username, password);
        assertThat(lastRegistration).isInstanceOf(RegistrationResult.Registered.class);
    }

    @När("jag registrerar mig med användarnamnet {string} och lösenordet {string}")
    public void jagRegistrerarMig(String username, String password) {
        lastRegistration = registrationService.register(username, password);
    }

    @När("jag försöker registrera mig med användarnamnet {string} och lösenordet {string}")
    public void jagFörsökerRegistreraMig(String username, String password) {
        lastRegistration = registrationService.register(username, password);
    }

    @Så("avvisas registreringen eftersom användarnamnet måste vara en e-postadress")
    public void avvisasEftersomInteEpost() {
        assertThat(lastRegistration).isInstanceOf(RegistrationResult.InvalidEmail.class);
    }

    @Så("avvisas registreringen eftersom användarnamnet är upptaget")
    public void avvisasEftersomUpptaget() {
        assertThat(lastRegistration).isInstanceOf(RegistrationResult.UsernameTaken.class);
    }

    @Så("blir registreringen godkänd precis som för en ny adress")
    public void registreringenGodkänd() {
        assertThat(lastRegistration).isInstanceOf(RegistrationResult.Registered.class);
    }

    @Och("angriparens verifieringslänk till {string} är ogiltig")
    public void angriparensLänkÄrOgiltig(String address) {
        assertThat(registrationService.checkVerificationToken(mailSender.sentTo(address).get(0).token()))
                .isEqualTo(TokenOutcome.INVALID);
    }

    @Och("den senaste verifieringslänken till {string} aktiverar kontot")
    public void senasteLänkenAktiverar(String address) {
        List<FakeMailSender.Mail> mails = mailSender.sentTo(address);
        assertThat(registrationService.verifyEmail(mails.get(mails.size() - 1).token()))
                .isEqualTo(TokenOutcome.SUCCESS);
    }

    @Och("inget konto med användarnamnet {string} har skapats")
    public void ingetKontoHarSkapats(String username) {
        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    @Så("finns ett overifierat konto {string}")
    public void finnsEttOverifieratKonto(String username) {
        assertThat(lastRegistration).isInstanceOf(RegistrationResult.Registered.class);
        assertThat(user(username).emailVerified()).isFalse();
    }

    @Och("ett mail med en verifieringslänk har skickats till {string}")
    public void ettMailMedVerifieringslänk(String address) {
        List<FakeMailSender.Mail> mails = mailSender.sentTo(address);
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).body()).contains("/verifiera?token=");
    }

    @Och("verifieringstokenet för {string} är bara lagrat som en hash")
    public void tokenetÄrBaraLagratSomHash(String username) {
        String raw = mailSender.sentTo(username).get(0).token();
        List<String> stored = jdbcTemplate.queryForList(
                "select token_hash from user_tokens where user_id = ?", String.class, user(username).id().value());
        assertThat(stored).containsExactly(TokenHasher.hash(raw));
        assertThat(stored).doesNotContain(raw);
    }

    // ---- Inloggning ---------------------------------------------------

    @När("jag försöker logga in som {string} med lösenordet {string}")
    public void jagFörsökerLoggaIn(String username, String password) throws Exception {
        lastLogin = login(username, password);
    }

    @Så("nekas inloggningen med ett meddelande om att adressen måste verifieras")
    public void nekasMedMeddelandeOmVerifiering() throws Exception {
        assertThat(lastLogin.getResponse().getRedirectedUrl()).isEqualTo("/login?unverified");
        String page = mockMvc.perform(get("/login").param("unverified", ""))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("inte verifierad");
    }

    @Så("jag kan logga in som {string} med lösenordet {string}")
    public void jagKanLoggaIn(String username, String password) throws Exception {
        assertThat(login(username, password).getResponse().getRedirectedUrl()).isEqualTo("/");
    }

    @Så("jag kan inte logga in som {string} med lösenordet {string}")
    public void jagKanInteLoggaIn(String username, String password) throws Exception {
        assertThat(login(username, password).getResponse().getRedirectedUrl()).startsWith("/login?");
    }

    // ---- Verifiering --------------------------------------------------

    @När("jag öppnar verifieringslänken i mailet till {string}")
    public void jagÖppnarVerifieringslänken(String address) {
        lastOutcome = registrationService.verifyEmail(mailSender.sentTo(address).get(0).token());
    }

    @Givet("att jag har öppnat verifieringslänken i mailet till {string}")
    public void attJagHarÖppnatVerifieringslänken(String address) {
        assertThat(registrationService.verifyEmail(mailSender.sentTo(address).get(0).token()))
                .isEqualTo(TokenOutcome.SUCCESS);
    }

    @När("jag öppnar verifieringslänken i mailet till {string} igen")
    public void jagÖppnarVerifieringslänkenIgen(String address) {
        jagÖppnarVerifieringslänken(address);
    }

    @Så("är kontot {string} verifierat")
    public void ärKontotVerifierat(String username) {
        assertThat(lastOutcome).isEqualTo(TokenOutcome.SUCCESS);
        assertThat(user(username).emailVerified()).isTrue();
    }

    @Och("kontot {string} är fortfarande overifierat")
    public void kontotÄrFortfarandeOverifierat(String username) {
        assertThat(user(username).emailVerified()).isFalse();
    }

    @Så("avvisas länken som ogiltig")
    public void avvisasLänkenSomOgiltig() {
        assertThat(lastOutcome).isEqualTo(TokenOutcome.INVALID);
    }

    @Så("avvisas länken som utgången")
    public void avvisasLänkenSomUtgången() {
        assertThat(lastOutcome).isEqualTo(TokenOutcome.EXPIRED);
    }

    @Givet("att det har gått {int} timmar")
    public void attDetHarGåttTimmar(int hours) {
        clock.advance(Duration.ofHours(hours));
    }

    @Och("att det har gått {int} minuter")
    public void attDetHarGåttMinuter(int minutes) {
        clock.advance(Duration.ofMinutes(minutes));
    }

    @När("jag begär en ny verifieringslänk för {string}")
    public void jagBegärEnNyVerifieringslänk(String address) {
        registrationService.resendVerification(address);
    }

    @Så("avvisas den första verifieringslänken som ogiltig")
    public void avvisasFörstaVerifieringslänken() {
        assertThat(registrationService.checkVerificationToken(allMails().get(0).token()))
                .isEqualTo(TokenOutcome.INVALID);
    }

    @Och("den nya verifieringslänken aktiverar kontot {string}")
    public void denNyaLänkenAktiverar(String username) {
        List<FakeMailSender.Mail> mails = mailSender.sentTo(username);
        assertThat(mails).hasSize(2);
        assertThat(registrationService.verifyEmail(mails.get(1).token())).isEqualTo(TokenOutcome.SUCCESS);
        assertThat(user(username).emailVerified()).isTrue();
    }

    // ---- Glömt lösenord -----------------------------------------------

    @Givet("att det finns ett verifierat konto {string} med lösenordet {string}")
    public void attDetFinnsEttVerifieratKonto(String username, String password) {
        testAccounts.register(username, password);
    }

    @När("jag öppnar inloggningssidan")
    public void jagÖppnarInloggningssidan() throws Exception {
        lastPage = mockMvc.perform(get("/login")).andReturn();
    }

    @Så("finns länken {string} till återställningsformuläret")
    public void finnsLänken(String text) throws Exception {
        String body = lastPage.getResponse().getContentAsString();
        assertThat(body).contains("href=\"/glomt-losenord\"").contains(text);
        assertThat(mockMvc.perform(get("/glomt-losenord")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @När("jag begär lösenordsåterställning för {string}")
    public void jagBegärÅterställning(String address) throws Exception {
        lastPage = mockMvc.perform(post("/glomt-losenord").param("username", address).with(csrf())).andReturn();
        responseBodies.add(withoutCsrf(lastPage.getResponse().getContentAsString()));
    }

    @När("jag begär lösenordsåterställning för {string} {int} gånger i rad")
    public void jagBegärÅterställningFleraGånger(String address, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            jagBegärÅterställning(address);
        }
    }

    @Givet("att jag har begärt lösenordsåterställning för {string}")
    public void attJagHarBegärtÅterställning(String address) throws Exception {
        jagBegärÅterställning(address);
    }

    @Givet("att jag har valt lösenordet {string} via länken i mailet till {string}")
    public void attJagHarValtLösenord(String password, String address) throws Exception {
        jagVäljerLösenord(password, address);
    }

    @Så("ett mail med en återställningslänk har skickats till {string}")
    public void ettMailMedÅterställningslänk(String address) {
        List<FakeMailSender.Mail> mails = mailSender.sentTo(address);
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).body()).contains("/aterstall-losenord?token=");
    }

    @Och("sidan visar det neutrala svaret {string}")
    public void sidanVisarNeutraltSvar(String text) throws Exception {
        assertThat(lastPage.getResponse().getContentAsString()).contains(text);
    }

    @Och("sidan visar fortfarande det neutrala svaret {string}")
    public void sidanVisarFortfarandeNeutraltSvar(String text) throws Exception {
        sidanVisarNeutraltSvar(text);
    }

    @Så("ger de två begäranden exakt samma svar")
    public void gerExaktSammaSvar() {
        assertThat(responseBodies).hasSize(2);
        assertThat(responseBodies.get(0)).isEqualTo(responseBodies.get(1));
    }

    @Och("inget mail har skickats till {string}")
    public void ingetMailHarSkickats(String address) {
        assertThat(mailSender.sentTo(address)).isEmpty();
    }

    @När("jag väljer lösenordet {string} via länken i mailet till {string}")
    public void jagVäljerLösenord(String password, String address) throws Exception {
        List<FakeMailSender.Mail> mails = mailSender.sentTo(address);
        String token = mails.get(mails.size() - 1).token();
        MvcResult result = mockMvc.perform(post("/aterstall-losenord")
                .param("token", token).param("password", password).param("confirmPassword", password)
                .with(csrf())).andReturn();
        lastOutcome = outcomeOf(result);
    }

    @Så("fungerar bara den senaste återställningslänken för {string}")
    public void barasenasteFungerar(String address) throws Exception {
        List<FakeMailSender.Mail> mails = mailSender.sentTo(address);
        assertThat(mails).hasSize(2);
        MvcResult old = mockMvc.perform(post("/aterstall-losenord").param("token", mails.get(0).token())
                .param("password", "x1").param("confirmPassword", "x1").with(csrf())).andReturn();
        assertThat(outcomeOf(old)).isEqualTo(TokenOutcome.INVALID);
        MvcResult latest = mockMvc.perform(post("/aterstall-losenord").param("token", mails.get(1).token())
                .param("password", "x2").param("confirmPassword", "x2").with(csrf())).andReturn();
        assertThat(outcomeOf(latest)).isEqualTo(TokenOutcome.SUCCESS);
    }

    @Och("att {string} är inloggad i en session")
    public void attÄrInloggadISession(String username) throws Exception {
        MvcResult result = login(username, "hemligt123");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        sessions.put(username, session);
        assertThat(mockMvc.perform(get("/").session(session)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Så("är den tidigare sessionen för {string} avslutad")
    public void sessionenÄrAvslutad(String username) throws Exception {
        assertThat(lastOutcome).isEqualTo(TokenOutcome.SUCCESS);
        MvcResult result = mockMvc.perform(get("/").session(sessions.get(username))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login");
    }

    @Så("har exakt {int} återställningsmail skickats till {string}")
    public void harExaktÅterställningsmail(int count, String address) {
        assertThat(mailSender.sentTo(address)).hasSize(count);
    }

    // ---- Hjälpare -----------------------------------------------------

    private MvcResult login(String username, String password) throws Exception {
        return mockMvc.perform(post("/login").param("username", username).param("password", password).with(csrf()))
                .andReturn();
    }

    private User user(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private List<FakeMailSender.Mail> allMails() {
        return mailSender.all();
    }

    private static TokenOutcome outcomeOf(MvcResult result) throws Exception {
        if ("/login?reset".equals(result.getResponse().getRedirectedUrl())) {
            return TokenOutcome.SUCCESS;
        }
        String body = result.getResponse().getContentAsString();
        if (body.contains("har gått ut")) {
            return TokenOutcome.EXPIRED;
        }
        if (body.contains("ogiltig eller redan använd")) {
            return TokenOutcome.INVALID;
        }
        throw new AssertionError("Oväntat svar: " + result.getResponse().getStatus() + " " + body);
    }

    private static String withoutCsrf(String html) {
        return html.replaceAll("name=\"_csrf\"\\s+value=\"[^\"]*\"", "name=\"_csrf\" value=\"X\"");
    }
}
