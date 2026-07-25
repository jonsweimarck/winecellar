package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @WebMvcTest/MockMvc kör ingen CSS och kan inte se att listan faktiskt
 * växlar mellan tabell (desktop) och kort (mobil) vid brytpunkten i
 * vinkallare.html - det är själva poängen med det responsiva UI:t, så det
 * verifieras här mot en riktigt renderad sida i två viewport-bredder.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class WineListResponsiveIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private WineService wineService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    private static final String TESTKONTO_ANVÄNDARNAMN = "wineListResponsiveTest";
    private static final String TESTKONTO_LÖSENORD = "testlösenord123";

    private UserId testkontoId;

    private static Playwright playwright;
    private static Browser browser;

    @BeforeAll
    static void startaBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void stängBrowser() {
        browser.close();
        playwright.close();
    }

    /**
     * WINE-15: inget hårdkodat konto kvar att logga in som (admin/readonly
     * borttagna) - registrerar ett riktigt testkonto istället, och sparar
     * dess UserId (behövs för att läggTillEttVin() ska kunna sätta rätt
     * ägare - annars vore vinet osynligt för det inloggade testkontot
     * sedan WINE-13s scoping). Idempotent (register(...) ignorerar bara
     * UsernameTaken-resultatet) eftersom detta körs i @BeforeEach, en
     * gång per test, mot samma delade Testcontainers-databas. Slås ihop
     * med läggTillEttVin() i EN @BeforeEach-metod, inte två separata -
     * JUnit 5 garanterar ingen körordning mellan flera @BeforeEach på
     * samma klass, och testkontoId måste vara satt INNAN vinet sparas.
     */
    @BeforeEach
    void säkerställTestkontoOchLäggTillEttVin() {
        registrationService.register(TESTKONTO_ANVÄNDARNAMN, TESTKONTO_LÖSENORD);
        testkontoId = userRepository.findByUsername(TESTKONTO_ANVÄNDARNAMN).orElseThrow().id();

        wineService.save(Wine.builder()
                .owner(testkontoId)
                .name("Barolo").wineType(WineType.RED).producer("Pio Cesare").country("Italien")
                .vintage(2018).quantity(3).location("Låda 1")
                .build());
    }

    @AfterEach
    void tömKällaren() {
        wineService.listWines(null).forEach(vin -> wineService.removeWine(vin.id(), null));
    }

    @Test
    void skaVisaTabellPåDesktopOchDöljaKort() {
        try (BrowserContext context = nyKontext(1280, 800, false)) {
            Page page = öppnaVinkällaren(context);

            assertThat(page.locator("#vinlista-tabell").isVisible()).isTrue();
            assertThat(page.locator("#vinlista-kort").isVisible()).isFalse();
            assertThat(page.locator("#vinlista-tabell").textContent()).contains("Barolo");
        }
    }

    @Test
    void skaVisaKortPåMobilOchDöljaTabell() {
        // isMobile(true) är avgörande, inte bara en smal setViewportSize: utan en
        // <meta name="viewport">-tagg i HTML:en renderar riktiga mobila webbläsare
        // sidan mot en betydligt bredare virtuell yta (~980px) och CSS-brytpunkten
        // triggas aldrig - ett rent setViewportSize(375, ...) missar den kvirken
        // helt (upptäcktes bara på en riktig telefon, inte av det här testet,
        // innan isMobile(true) lades till - se CLAUDE.md).
        try (BrowserContext context = nyKontext(375, 667, true)) {
            Page page = öppnaVinkällaren(context);

            assertThat(page.locator("#vinlista-kort").isVisible()).isTrue();
            assertThat(page.locator("#vinlista-tabell").isVisible()).isFalse();
            assertThat(page.locator("#vinlista-kort").textContent()).contains("Barolo");
        }
    }

    @Test
    void skaVisaRedigeraOchTaBortDirektPåDesktop() {
        // De breda korten (desktop, >960px) har ingen infälld Detaljer -
        // allt, inklusive Redigera/Ta bort, visas direkt utan att något
        // behöver fällas ut. Till skillnad från kortvyn (mobil) nedan,
        // som fortfarande döljer dem tills "Detaljer" klickas.
        try (BrowserContext context = nyKontext(1280, 800, false)) {
            Page page = öppnaVinkällaren(context);
            Locator tabell = page.locator("#vinlista-tabell");

            assertThat(tabell.locator("text=Redigera").isVisible()).isTrue();
            assertThat(tabell.locator("text=Ta bort").isVisible()).isTrue();
        }
    }

    @Test
    void skaVisaAllaFältDirektPåDesktopUtanAttFällaUtNågot() {
        wineService.save(Wine.builder()
                .owner(testkontoId)
                .name("Chablis").wineType(WineType.WHITE).producer("Domaine X").country("Frankrike")
                .vintage(2020).quantity(2).location("Låda 3")
                .tastingNotes("Mineralisk och frisk")
                .build());

        try (BrowserContext context = nyKontext(1280, 800, false)) {
            Page page = öppnaVinkällaren(context);
            Locator tabell = page.locator("#vinlista-tabell");

            assertThat(tabell.locator("text=Mineralisk och frisk").isVisible()).isTrue();
        }
    }

    @Test
    void skaVisaFlaskbadgeOchDöljaRedigeraOchTaBortTillsDetaljerFällsUtPåMobil() {
        // isMobile(true) krävs för att CSS-brytpunkten alls ska slå till, se
        // skaVisaKortPåMobilOchDöljaTabell ovan för bakgrunden.
        try (BrowserContext context = nyKontext(375, 900, true)) {
            Page page = öppnaVinkällaren(context);
            Locator kort = page.locator("#vinlista-kort");

            assertThat(kort.locator(".flaskor-badge").textContent()).isEqualTo("3");
            assertThat(kort.locator("text=Redigera").isVisible()).isFalse();

            kort.locator("summary:has-text(\"Detaljer\")").click();

            assertThat(kort.locator("text=Redigera").isVisible()).isTrue();
            assertThat(kort.locator("text=Ta bort").isVisible()).isTrue();
        }
    }

    /**
     * WINE-12: formulärinloggning med session ersatte HTTP Basic
     * (`setHttpCredentials`, som Playwright annars hade skött automatiskt
     * på varje request) - inloggningen görs nu en gång som en riktig
     * sidnavigering/formulärinskick, varefter sessionscookien (satt på
     * BrowserContext-nivå av Playwright, inte Page-nivå) följer med alla
     * senare sidor som öppnas i samma kontext. Loggar alltid in som
     * `TESTKONTO_ANVÄNDARNAMN` sedan WINE-15 (admin/readonly borttagna,
     * och testerna i den här klassen bryr sig bara om CSS-/
     * responsivitetsbeteende, inte om VEM som är inloggad - riktig
     * multi-user-testning finns i MultiUserSteps/WineControllerTest
     * istället).
     */
    private BrowserContext nyKontext(int bredd, int höjd, boolean mobil) {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(bredd, höjd)
                .setIsMobile(mobil)
                .setHasTouch(mobil));
        loggaIn(context);
        return context;
    }

    private void loggaIn(BrowserContext context) {
        Page inloggningssida = context.newPage();
        inloggningssida.navigate("http://localhost:" + port + "/login");
        inloggningssida.locator("#username").fill(TESTKONTO_ANVÄNDARNAMN);
        inloggningssida.locator("#password").fill(TESTKONTO_LÖSENORD);
        inloggningssida.locator("button[type=submit]").click();
        inloggningssida.waitForLoadState();
        inloggningssida.close();
    }

    private Page öppnaVinkällaren(BrowserContext context) {
        Page page = context.newPage();
        page.navigate("http://localhost:" + port + "/");
        return page;
    }
}
