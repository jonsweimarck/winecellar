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
import com.example.winecellar.support.SharedPostgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @WebMvcTest/MockMvc kör ingen CSS och kan inte se att listan faktiskt
 * växlar mellan tabell (desktop) och kort (mobil) vid brytpunkten i
 * vinkallare.html - det är själva poängen med det responsiva UI:t, så det
 * verifieras här mot en riktigt renderad sida i två viewport-bredder.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class WineListResponsiveIT extends SharedPostgres {

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
    void skaVisaRedigeraIkonDirektPåDesktop() {
        // De breda korten (desktop, >960px) har ingen infälld Detaljer -
        // Redigera-ikonen visas direkt utan att något behöver fällas ut.
        // Till skillnad från kortvyn (mobil) nedan, som fortfarande döljer
        // den tills "Detaljer" klickas. "Ta bort" finns inte längre kvar i
        // listan alls sedan WINE-44 - se VinFormularIT för raderingsflödet.
        try (BrowserContext context = nyKontext(1280, 800, false)) {
            Page page = öppnaVinkällaren(context);
            Locator tabell = page.locator("#vinlista-tabell");

            assertThat(tabell.locator("a[aria-label='Redigera']").isVisible()).isTrue();
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
    void skaVisaFlaskbadgeOchDöljaRedigeraIkonTillsDetaljerFällsUtPåMobil() {
        // isMobile(true) krävs för att CSS-brytpunkten alls ska slå till, se
        // skaVisaKortPåMobilOchDöljaTabell ovan för bakgrunden.
        try (BrowserContext context = nyKontext(375, 900, true)) {
            Page page = öppnaVinkällaren(context);
            Locator kort = page.locator("#vinlista-kort");

            assertThat(kort.locator(".flaskor-badge").textContent()).isEqualTo("3");
            assertThat(kort.locator("a[aria-label='Redigera']").isVisible()).isFalse();

            kort.locator("summary:has-text(\"Detaljer\")").click();

            assertThat(kort.locator("a[aria-label='Redigera']").isVisible()).isTrue();
        }
    }

    /**
     * WINE-38: "Lägg till vin" som flytande rund knapp (FAB) på mobil
     * istället för den vanliga radknappen som visas på desktop - se
     * kommentaren vid `.knapp-lagg-till-fab` i vinkallare.html.
     */
    @Test
    void skaVisaFabKnappenPåMobilOchDöljaDenVanligaKnappen() {
        try (BrowserContext context = nyKontext(375, 667, true)) {
            Page page = öppnaVinkällaren(context);

            assertThat(page.locator(".knapp-lagg-till-fab").isVisible()).isTrue();
            assertThat(page.locator(".knapp-lagg-till-desktop").isVisible()).isFalse();
        }
    }

    @Test
    void skaVisaDenVanligaKnappenPåDesktopOchDöljaFabKnappen() {
        try (BrowserContext context = nyKontext(1280, 800, false)) {
            Page page = öppnaVinkällaren(context);

            assertThat(page.locator(".knapp-lagg-till-desktop").isVisible()).isTrue();
            assertThat(page.locator(".knapp-lagg-till-fab").isVisible()).isFalse();
        }
    }

    /**
     * WINE-41: verktygsraden tog 217px på en 812px-skärm innan första
     * vinet syntes. Testet låser inte en exakt höjd (det skulle gå sönder
     * vid varje smärre justering) utan de två strukturella besluten som
     * ger komprimeringen: sökfältet får en egen full rad, och sortering
     * och riktning delar den nästa. Faller något av dem tillbaka till
     * staplade helbreddskontroller växer raden igen utan att någon
     * märker det.
     */
    @Test
    void skaHållaVerktygsradenKompaktPåMobil() {
        try (BrowserContext context = nyKontext(375, 812, true)) {
            Page page = öppnaVinkällaren(context);

            assertThat(bredd(page, "#search")).isGreaterThan(300);
            assertThat(överkant(page, "#sort")).isEqualTo(överkant(page, "#direction"));
            assertThat(överkant(page, "#sort")).isGreaterThan(överkant(page, "#search"));

            // Sökfältets etikett döljs visuellt men måste finnas kvar för
            // skärmläsare - tas den bort helt blir fältet omärkt.
            assertThat(page.locator(".sok-grupp label").textContent()).isNotBlank();
        }
    }

    /**
     * En tom källare och ett sökresultat utan träffar är HELT skilda
     * situationer: den första behöver en väg in i appen, den andra en väg
     * tillbaka till hela listan. Verktygsraden döljs bara i det första
     * fallet - göms den i det andra går sökningen inte att ta bort igen.
     */
    @Test
    void skaVisaEnInbjudanIStalletForVerktygsradNarKallarenArTom() {
        wineService.listWines(testkontoId).forEach(vin -> wineService.removeWine(vin.id(), testkontoId));

        try (BrowserContext context = nyKontext(375, 812, true)) {
            Page page = öppnaVinkällaren(context);

            assertThat(page.locator(".tomt-lage").textContent()).contains("Källaren är tom");
            assertThat(page.locator("form[hx-get]").count()).isZero();
            assertThat(page.locator(".tomt-lage a[href='/wines/nytt']").isVisible()).isTrue();
        }
    }

    @Test
    void skaBehållaVerktygsradenNärSökningenSaknarTräffar() {
        try (BrowserContext context = nyKontext(375, 812, true)) {
            Page page = context.newPage();
            page.navigate("http://localhost:" + port + "/?search=finnsdefinitivtinte");

            assertThat(page.locator(".tomt-lage").textContent()).contains("Inga viner matchar");
            assertThat(page.locator("form[hx-get]").count()).isOne();
        }
    }

    private int bredd(Page page, String väljare) {
        return (int) page.locator(väljare).boundingBox().width;
    }

    private int överkant(Page page, String väljare) {
        return (int) page.locator(väljare).boundingBox().y;
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
