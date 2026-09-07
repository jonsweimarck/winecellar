package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.support.SharedPostgres;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Vinformulärets klientsidesbeteende (WINE-39/WINE-40): svenska
 * valideringsmeddelanden, den egna filväljaren och markeringen av
 * obligatoriska fält. Inget av det syns i renderad HTML på ett sätt
 * MockMvc kan bedöma - meddelandetexterna finns bara i webbläsarens egen
 * valideringsmodell, och filväljarens knapp är en label vars funktion
 * beror på att den dolda inputen fortfarande fungerar.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class VinFormularIT extends SharedPostgres {

    @LocalServerPort
    private int port;

    @Autowired
    private RegistrationService registrationService;

    private static final String TESTKONTO_ANVÄNDARNAMN = "vinFormularTest";
    private static final String TESTKONTO_LÖSENORD = "testlösenord123";

    private static final byte[] EN_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

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

    @Test
    void skaVisaValideringsmeddelandenPåSvenska() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(context);

            assertThat(valideringsmeddelande(sida, "name")).isEqualTo("Fyll i det här fältet.");
        }
    }

    /**
     * Ett fält vars antalsvärde ligger under min ska INTE få
     * "fyll i fältet"-texten - då beskriver meddelandet fel problem och
     * användaren får ingen ledtråd om vad som faktiskt är fel.
     */
    @Test
    void skaGeEttEgetMeddelandeFörEttAntalUnderNoll() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(context);

            sida.locator("input[name=quantity]").fill("-5");

            assertThat(valideringsmeddelande(sida, "quantity")).isEqualTo("Ange ett värde som är minst 0.");
        }
    }

    /**
     * REGRESSIONSSKYDD för den klassiska setCustomValidity-fällan: ett
     * fält med ett satt eget meddelande räknas som ogiltigt tills
     * meddelandet uttryckligen nollställs. Nollställs det inte går
     * formuläret ALDRIG att skicka, ens korrekt ifyllt - och felet är
     * tyst, utan felmeddelande någonstans. Se static/js/validering.js.
     */
    @Test
    void skaGåAttSparaEfterAttEttOgiltigtFältRättatsTill() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(context);

            // Trigga valideringen först, så det egna meddelandet hinner sättas.
            sida.locator("button[type=submit]").last().click();
            assertThat(sida.url()).contains("/wines/nytt");

            sida.locator("input[name=name]").fill("Barolo Riserva");
            sida.locator("input[name=quantity]").fill("2");
            sida.locator("button[type=submit]").last().click();

            sida.waitForURL(url("/"));
            assertThat(sida.locator("body").textContent()).contains("Barolo Riserva");
        }
    }

    /**
     * WINE-42: hela kedjan i ett enda test - redirecten från POST /wines
     * bär meddelandet som ett flash-attribut, GET / plockar upp det och
     * renderar toasten, och JS-timern tonar bort den efter 3 sekunder.
     * Ett WebMvcTest kan bevisa att flash-attributet sätts (se
     * WineControllerTest), men bara en riktig webbläsare kan bevisa att
     * den faktiskt SYNS och sedan FÖRSVINNER - CSS-transitions och
     * setTimeout körs inte i MockMvc.
     */
    @Test
    void skaVisaOchTonaBortEnBekräftelseEfterAttEttVinSparats() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(context);
            // Eget namn, inte "Barolo Riserva" som andra test i den här
            // klassen redan sparar mot samma delade testkonto (ingen
            // städning mellan testmetoder) - samma namn hade gett en
            // dubblettvarning i stället för en redirect, och
            // waitForURL("/") nedan hade time:at ut helt tyst.
            sida.locator("input[name=name]").fill("Toast-testvin");
            sida.locator("input[name=quantity]").fill("2");
            sida.locator("button[type=submit]").last().click();
            sida.waitForURL(url("/"));

            // trim(): textContent() tar med den formaterande whitespacen
            // runt ikon-SVG:n och textspannet i markupen, inte bara
            // själva orden.
            assertThat(sida.locator(".toast").textContent().trim()).isEqualTo("Vin tillagt");
            assertThat(sida.locator(".toast").getAttribute("class")).doesNotContain("toast-dold");

            // setTimeout i vinkallare.html är satt till 3000ms.
            sida.waitForTimeout(3300);
            assertThat(sida.locator(".toast").getAttribute("class")).contains("toast-dold");
        }
    }

    @Test
    void skaMarkeraExaktDeFältSomÄrObligatoriska() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(context);

            // Jämför de MARKERADE fälten mot de som faktiskt har
            // required-attributet, i stället för mot en hårdkodad lista -
            // då fångar testet även att någon lägger till ett nytt
            // obligatoriskt fält utan att markera det i UI:t.
            assertThat(fältnamn(sida, ".falt:has(.obligatoriskt) input"))
                    .isEqualTo(fältnamn(sida, "form [required]"))
                    .isEqualTo("name,quantity");
        }
    }

    /**
     * Webbläsarens egen filknapp går varken att styla eller översätta, så
     * inputen döljs och en label används som knapp (se .filval i
     * tema.css). Döljs den med display:none i stället för clip försvinner
     * den ur tabbordningen - därav fokustestet.
     */
    @Test
    void skaDöljaFilinputenMenBehållaDenAnvändbar() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(context);

            // Playwrights isVisible() duger inte som assertion här: den
            // dolda inputen har en ruta på 1x1 pixel (det är precis vad
            // clip-tekniken innebär) och räknas därför som synlig. Mät
            // ytan i stället - den ska vara försumbar.
            assertThat(ytaIPixlar(sida, "#bild-input")).isLessThanOrEqualTo(1);
            assertThat(sida.locator("label[for=bild-input]").textContent().trim()).isEqualTo("Välj bild");

            // Kvar i tabbordningen - försvinner med display:none.
            sida.locator("#bild-input").focus();
            assertThat(sida.evaluate("() => document.activeElement.id")).isEqualTo("bild-input");
        }
    }

    /**
     * Knappen är en label - poängen är att den faktiskt öppnar den dolda
     * inputens filväljare. Bryts kopplingen (fel `for`, fel id) ser
     * knappen fortfarande helt normal ut men gör ingenting alls.
     */
    @Test
    void skaÖppnaFilväljarenNärDenEgnaKnappenKlickas() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(context);

            FileChooser väljare = sida.waitForFileChooser(
                    () -> sida.locator("label[for=bild-input]").click());

            assertThat(väljare.element().getAttribute("id")).isEqualTo("bild-input");
        }
    }

    @Test
    void skaVisaFilnamnetNärEnBildValts() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(context);
            assertThat(sida.locator("#bild-namn").textContent().trim()).isEqualTo("Ingen bild vald");

            sida.locator("#bild-input").setInputFiles(
                    new FilePayload("etikett.png", "image/png", EN_PIXEL_PNG));

            assertThat(sida.locator("#bild-namn").textContent().trim()).isEqualTo("etikett.png");
        }
    }

    /**
     * Importsidans Excel-fält är `required` OCH dolt. En dold obligatorisk
     * kontroll som webbläsaren inte kan fokusera gör att inskicket
     * blockeras tyst, utan bubbla - sidan verkar då bara "inte reagera" på
     * knappen.
     */
    @Test
    void skaBlockeraImportUtanFilMedEttSynligtMeddelande() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = context.newPage();
            sida.navigate(url("/import"));

            sida.locator("#import-submit").click();

            assertThat(sida.url()).contains("/import");
            assertThat(valideringsmeddelande(sida, "fil")).isEqualTo("Välj en fil.");
        }
    }

    /**
     * Elementets renderade yta i kvadratpixlar. Casten går via Number,
     * inte Double: Playwright lämnar tillbaka ett Integer när JS-talet
     * råkar vara helt, och ett Double annars.
     */
    private double ytaIPixlar(Page sida, String väljare) {
        return ((Number) sida.evaluate(
                "väljare => { const r = document.querySelector(väljare).getBoundingClientRect();"
                        + " return r.width * r.height; }",
                väljare)).doubleValue();
    }

    /** Namnen på de fält en CSS-väljare träffar, i dokumentordning. */
    private String fältnamn(Page sida, String väljare) {
        return (String) sida.evaluate(
                "väljare => Array.from(document.querySelectorAll(väljare)).map(f => f.name).join(',')",
                väljare);
    }

    private String valideringsmeddelande(Page sida, String fältnamn) {
        return (String) sida.evaluate(
                "namn => { const f = document.querySelector(`[name=${namn}]`);"
                        + " f.reportValidity(); return f.validationMessage; }",
                fältnamn);
    }

    private Page öppnaFormuläret(BrowserContext context) {
        Page sida = context.newPage();
        sida.navigate(url("/wines/nytt"));
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
