package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.support.SharedPostgres;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * WINE-45: "Antal flaskor minst" sparas numera automatiskt (JS-driven
 * `change`-lyssnare, se installningar.html) i stället för via en manuell
 * "Spara"-knapp - samma serverrunda (POST + helsidesredirect) som
 * knappen tidigare utlöste. Verifierat mot en riktig webbläsare av samma
 * skäl som `TemaIT`/`WineListResponsiveIT` finns: MockMvc/@WebMvcTest kan
 * bevisa att markupen saknar knappen och att POST-rutten fortfarande
 * sparar rätt värde (se `SettingsControllerTest`), men inte att en
 * FAKTISK fältändring i webbläsaren faktiskt utlöser den serverrundan
 * utan något knapptryck.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class SettingsIT extends SharedPostgres {

    @LocalServerPort
    private int port;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WineService wineService;

    private static final String TESTKONTO_ANVÄNDARNAMN = "installningarTest";
    private static final String TESTKONTO_LÖSENORD = "testlösenord123";

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

    @BeforeEach
    void säkerställTestkonto() {
        registrationService.register(TESTKONTO_ANVÄNDARNAMN, TESTKONTO_LÖSENORD);
    }

    /**
     * `wineService.listWines(null)` returnerar SAMTLIGA viner, oavsett
     * ägare (se CLAUDE.md) - samma städmönster som `WineListResponsiveIT`
     * använder, eftersom alla IT-klasser delar en Postgres-container.
     */
    @AfterEach
    void tömKällaren() {
        wineService.listWines(null).forEach(vin -> wineService.removeWine(vin.id(), null));
    }

    @Test
    void skaInteVisaNågonSparaKnappFörAntalFlaskorFiltret() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaInställningar(context);

            assertThat(sida.locator("#antalFlaskorFilterFormular button[type=submit]").count()).isZero();
            assertThat(sida.locator("#antalFlaskorFilterFormular").getByText("Spara").count()).isZero();
        }
    }

    /**
     * Kärnan i storyn: en ändring av fältet - utan att röra något
     * knapptryck - ska faktiskt trigga serverrundan och landa som ett
     * bestående värde, verifierat genom att navigera bort och tillbaka.
     */
    @Test
    void skaSparaÄndratVärdeAutomatisktUtanKnapptryckning() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaInställningar(context);

            sida.locator("#minQuantityFilterFalt").fill("5");
            sida.locator("#minQuantityFilterFalt").press("Tab");
            sida.waitForLoadState();

            sida.navigate(url("/"));
            sida.navigate(url("/installningar"));
            assertThat(sida.locator("#minQuantityFilterFalt").inputValue()).isEqualTo("5");
        }
    }

    /**
     * Robusthetsfynd från granskningen: ett klick på en angränsande länk
     * (t.ex. "Exportera") direkt efter en fältändring - utan att först
     * tabba bort fältet - hinner annars starta två nästan samtidiga
     * navigeringar (blurens auto-submit och länkens egen navigering), med
     * risk att det ändrade värdet tyst går förlorat beroende på
     * webbläsare. Skriptet i installningar.html avbryter klicket medan
     * auto-submitten pågår - verifierat här genom att klicka direkt på
     * "Exportera"-länken utan något Tab-tryck emellan, och sedan bekräfta
     * att värdet ändå sparades.
     */
    @Test
    void skaInteTappaÄndratVärdeVidKlickPåAnnanLänkUtanAttFörstTabbaBort() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaInställningar(context);

            sida.locator("#minQuantityFilterFalt").fill("9");
            sida.locator("a[href='/export']").click();
            sida.waitForLoadState();

            sida.navigate(url("/installningar"));
            assertThat(sida.locator("#minQuantityFilterFalt").inputValue()).isEqualTo("9");
        }
    }

    /**
     * Bevisar att det sparade värdet faktiskt ANVÄNDS, inte bara att det
     * råkar stå kvar i inställningsfältet - samma sparade default som
     * `WineController.wineCellar(...)` faller tillbaka till när `GET /`
     * saknar en explicit `minQuantity`-queryparameter (se CLAUDE.md).
     * Kräver minst ett vin i källaren - verktygsraden (och därmed
     * filterfältet) döljs helt för en helt tom källare (se CLAUDE.md,
     * "Vinlistan har två skilda tomma lägen").
     */
    @Test
    void skaAnvändaDetSparadeVärdetSomVinlistansStandardfilterEfterFörstaSidladdningen() {
        wineService.save(Wine.builder()
                .owner(userRepository.findByUsername(TESTKONTO_ANVÄNDARNAMN).orElseThrow().id())
                .name("Barolo").quantity(3)
                .build());

        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaInställningar(context);

            sida.locator("#minQuantityFilterFalt").fill("7");
            sida.locator("#minQuantityFilterFalt").press("Tab");
            sida.waitForLoadState();

            sida.navigate(url("/"));
            assertThat(sida.locator("#minQuantity").inputValue()).isEqualTo("7");
        }
    }

    private Page öppnaInställningar(BrowserContext context) {
        Page sida = context.newPage();
        sida.navigate(url("/installningar"));
        return sida;
    }

    private BrowserContext nyInloggadKontext() {
        BrowserContext context = browser.newContext();
        Page inloggningssida = context.newPage();
        inloggningssida.navigate(url("/login"));
        inloggningssida.locator("#username").fill(TESTKONTO_ANVÄNDARNAMN);
        inloggningssida.locator("#password").fill(TESTKONTO_LÖSENORD);
        inloggningssida.locator("button[type=submit]").click();
        inloggningssida.waitForLoadState();
        inloggningssida.close();
        return context;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
