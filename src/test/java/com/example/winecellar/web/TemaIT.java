package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.support.SharedPostgres;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Temaväxlingen (WINE-39, se ADR 0019) är helt CSS- och JS-driven -
 * ingenting av den syns i renderad HTML, så varken MockMvc eller ett
 * @WebMvcTest kan verifiera den. Testas därför mot en riktig webbläsare,
 * av samma skäl som `WineListResponsiveIT` finns.
 *
 * Assertionerna jämför ljust mot mörkt läge RELATIVT (mörk bakgrund ska
 * vara mörkare än ljus) i stället för att låsa fast exakta färgvärden -
 * ett test som kräver ett visst hexvärde går sönder varje gång paletten
 * justeras, utan att något faktiskt är trasigt.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class TemaIT extends SharedPostgres {

    @LocalServerPort
    private int port;

    @Autowired
    private RegistrationService registrationService;

    private static final String TESTKONTO_ANVÄNDARNAMN = "temaTest";
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
     * Stilmallen och valideringsskriptet måste vara åtkomliga UTAN
     * inloggning: inloggningssidan renderas per definition för en anonym
     * besökare. Missas undantaget i SecurityConfig omdirigeras de till
     * /login och sidan visas helt ostylad - ett fel som annars bara
     * upptäcks med ögat.
     */
    @Test
    void skaServeraStilmallOchSkriptTillEnAnonymBesökare() {
        try (BrowserContext context = browser.newContext()) {
            APIResponse stilmall = context.request().get(url("/css/tema.css"));
            assertThat(stilmall.status()).isEqualTo(200);
            assertThat(stilmall.text()).contains("--accent");

            APIResponse skript = context.request().get(url("/js/validering.js"));
            assertThat(skript.status()).isEqualTo(200);
        }
    }

    @Test
    void skaFöljaSystemetTillsAnvändarenValtNågotAnnat() {
        try (BrowserContext context = browser.newContext()) {
            Page sida = context.newPage();
            sida.navigate(url("/login"));

            assertThat(temaAttribut(sida)).isEqualTo("auto");
        }
    }

    @Test
    void skaByteTillMörktLägeOchFaktisktÄndraFärgerna() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaInställningar(context);
            String ljusBakgrund = bakgrundsfärg(sida);

            väljTema(sida, "mork");

            assertThat(temaAttribut(sida)).isEqualTo("mork");
            assertThat(ärMörkare(bakgrundsfärg(sida), ljusBakgrund)).isTrue();
        }
    }

    /**
     * Kärnan i hela mekanismen: valet sparas i localStorage och läses av
     * fragments/tema.html vid VARJE sidladdning. Går det förlorat vid en
     * sidnavigering är funktionen i praktiken oanvändbar, trots att
     * själva knappen ser ut att fungera.
     */
    @Test
    void skaBehållaValtTemaVidNavigeringTillEnAnnanSida() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaInställningar(context);
            väljTema(sida, "mork");

            sida.navigate(url("/"));
            assertThat(temaAttribut(sida)).isEqualTo("mork");

            sida.navigate(url("/wines/nytt"));
            assertThat(temaAttribut(sida)).isEqualTo("mork");
        }
    }

    /**
     * Temat är inte kontobundet utan webbläsarbundet (se ADR 0019), så
     * det måste gälla även på de utloggade sidorna - annars blinkar
     * appen vitt vid varje utloggning för den som valt mörkt läge.
     */
    @Test
    void skaBehållaValtTemaÄvenPåDenAnonymaInloggningssidan() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaInställningar(context);
            väljTema(sida, "mork");
            String mörkBakgrund = bakgrundsfärg(sida);

            sida.locator("button.installningsrad-farlig").click();
            sida.waitForURL("**/login**");

            assertThat(temaAttribut(sida)).isEqualTo("mork");
            assertThat(bakgrundsfärg(sida)).isEqualTo(mörkBakgrund);
        }
    }

    @Test
    void skaMarkeraDetValdaTemaletSomAktivt() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaInställningar(context);

            väljTema(sida, "ljust");

            assertThat(sida.locator("[data-tema-val=ljust]").getAttribute("aria-pressed")).isEqualTo("true");
            assertThat(sida.locator("[data-tema-val=mork]").getAttribute("aria-pressed")).isEqualTo("false");
            assertThat(sida.locator("[data-tema-val=auto]").getAttribute("aria-pressed")).isEqualTo("false");
        }
    }

    private void väljTema(Page sida, String tema) {
        sida.locator("[data-tema-val=" + tema + "]").click();
    }

    private String temaAttribut(Page sida) {
        return (String) sida.evaluate("() => document.documentElement.getAttribute('data-tema')");
    }

    private String bakgrundsfärg(Page sida) {
        return (String) sida.evaluate("() => getComputedStyle(document.body).backgroundColor");
    }

    /**
     * Jämför två "rgb(r, g, b)"-strängar på summerad ljusstyrka. Grovt,
     * men tillräckligt för frågan testet ställer: bytte temat faktiskt
     * riktning? Exakta värden undviks medvetet, se klasskommentaren.
     */
    private boolean ärMörkare(String färg, String jämförtMed) {
        return ljusstyrka(färg) < ljusstyrka(jämförtMed);
    }

    private int ljusstyrka(String rgb) {
        String[] delar = rgb.replaceAll("[^0-9,]", "").split(",");
        return Integer.parseInt(delar[0]) + Integer.parseInt(delar[1]) + Integer.parseInt(delar[2]);
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
