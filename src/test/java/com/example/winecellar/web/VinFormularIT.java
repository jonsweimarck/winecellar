package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.support.SharedPostgres;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
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

    /**
     * WINE-44: "Ta bort" flyttades från vinlistan till en bekräftad
     * radering på redigera-sidan, byggd med ett native &lt;dialog&gt;-
     * element i stället för window.confirm() (se kommentaren i
     * vin-formular.html för varför). Hela kedjan i ett enda test - öppna
     * dialogen, bekräfta, redirect till startsidan, och toasten som visas
     * och sedan tonas bort - på samma sätt som
     * skaVisaOchTonaBortEnBekräftelseEfterAttEttVinSparats ovan gör för
     * lägg-till-vägen. Ingen av de två kan bevisas av WineControllerTest
     * (som bara ser att flash-attributet sätts, se
     * WineControllerTest.NärEttVinRaderas) - bara en riktig webbläsare kan
     * bevisa att dialogen faktiskt går att öppna/bekräfta och att toasten
     * SYNS och sedan FÖRSVINNER.
     */
    @Test
    void skaRaderaVinetEfterBekräftelseIDialogenOchTonaBortToasten() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(context);
            sida.locator("input[name=name]").fill("Raderingstestvin");
            sida.locator("input[name=quantity]").fill("1");
            sida.locator("button[type=submit]").last().click();
            sida.waitForURL(url("/"));

            sida.locator("#vinlista-tabell .vinkort-bred", new Page.LocatorOptions().setHasText("Raderingstestvin"))
                    .locator("a[aria-label='Redigera']")
                    .click();
            sida.waitForURL(url("**/redigera"));

            // Bara knappen som öppnar dialogen är klickad hittills -
            // ingenting ska ha skickats in än.
            sida.locator("#oppna-radera-dialog").click();
            assertThat(sida.locator("#radera-dialog").isVisible()).isTrue();

            sida.locator("#radera-dialog button[type=submit]").click();
            sida.waitForURL(url("/"));

            // trim(): textContent() tar med den formaterande whitespacen
            // runt ikon-SVG:n och textspannet i markupen, inte bara
            // själva orden.
            assertThat(sida.locator(".toast").textContent().trim()).isEqualTo("Vin borttaget");
            assertThat(sida.locator(".toast").getAttribute("class")).doesNotContain("toast-dold");

            // setTimeout i vinkallare.html är satt till 3000ms.
            sida.waitForTimeout(3300);
            assertThat(sida.locator(".toast").getAttribute("class")).contains("toast-dold");

            assertThat(sida.locator("body").textContent()).doesNotContain("Raderingstestvin");
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
     * WINE-52: den tidigare rena &lt;datalist&gt;-autocompleten (WINE-51)
     * visade sig inte fungera alls i mobila webbläsare - iOS Safari
     * saknar helt stöd för &lt;datalist&gt; på textinputs (ett
     * långvarigt, aldrig åtgärdat WebKit-beteende), och Android Chrome
     * har historiskt haft inkonsekvent/opålitligt stöd. Fixen ersätter
     * datalist-kopplingen med en egen, enkel JS-dropdown (se
     * vin-formular.html) som beter sig identiskt oavsett plattform.
     * isMobile(true)/hasTouch(true) krävs för att över huvud taget
     * spegla en riktig mobil webbläsarkontext, inte bara en smal
     * setViewportSize - se CLAUDE.md.
     */
    @Test
    void skaVisaAutocompleteFörslagIEnMobilWebbläsarkontext() {
        try (BrowserContext context = nyInloggadKontext(true)) {
            // Lägg till ett första vin med en tagg, så att taggen finns
            // med som ett tidigare använt förslag när nästa vin läggs
            // till.
            Page förstaVinet = öppnaFormuläret(context);
            förstaVinet.locator("input[name=name]").fill("Barolo (mobilförslag)");
            förstaVinet.locator("input[name=quantity]").fill("1");
            förstaVinet.locator("#tagg-input").fill("Favorit");
            förstaVinet.locator("#tagg-lagg-till").click();
            förstaVinet.locator("button[type=submit]").last().click();
            förstaVinet.waitForURL(url("/"));

            Page sida = öppnaFormuläret(context);
            Locator förslagslista = sida.locator("#tagg-forslagslista");
            assertThat(förslagslista.isVisible()).isFalse();

            sida.locator("#tagg-input").fill("fav");

            assertThat(förslagslista.isVisible()).isTrue();
            assertThat(förslagslista.locator("li").first().textContent()).isEqualTo("Favorit");

            // Att klicka en förslagspost lägger till den direkt som en
            // tagg (samma semantik som Enter på en tangentbordsmarkerad
            // post, se skaVäljaEttMarkeratFörslagMedTangentbordetOchEnter
            // nedan) - inte bara en ifylld textruta som väntar på ett
            // separat klick på "Ny tagg".
            förslagslista.locator("li").first().click();
            assertThat(sida.locator("#tagg-input").inputValue()).isEmpty();
            assertThat(förslagslista.isVisible()).isFalse();
            assertThat(sida.locator("#tagg-chips .chip-tagg").last().textContent().trim()).isEqualTo("Favorit ×");
        }
    }

    /**
     * WINE-52 (granskningsfynd, PR #34): den ursprungliga JS-dropdownen
     * hanterade bara musklick på en förslagspost - ArrowDown/ArrowUp/
     * Enter gjorde ingenting alls i listan, så Enter gick i stället
     * direkt till den vanliga "lägg till det skrivna som fritext"-
     * logiken. Konkret regression mot den gamla datalist-lösningen
     * (som i skrivbordswebbläsare redan stödde piltangenter+Enter):
     * användaren skriver "fav", ser förslaget "Favorit", men kunde inte
     * välja det med bara tangentbordet - Enter hade lagt till en
     * FELAKTIG tagg ("fav" ordagrant) i stället.
     */
    @Test
    void skaVäljaEttMarkeratFörslagMedTangentbordetOchEnter() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page förstaVinet = öppnaFormuläret(context);
            förstaVinet.locator("input[name=name]").fill("Barolo (tangentbordsval)");
            förstaVinet.locator("input[name=quantity]").fill("1");
            förstaVinet.locator("#tagg-input").fill("Favorit");
            förstaVinet.locator("#tagg-lagg-till").click();
            förstaVinet.locator("button[type=submit]").last().click();
            förstaVinet.waitForURL(url("/"));

            Page sida = öppnaFormuläret(context);
            Locator input = sida.locator("#tagg-input");
            input.fill("fav");
            input.press("ArrowDown");
            input.press("Enter");

            assertThat(input.inputValue()).isEmpty();
            assertThat(sida.locator("#tagg-forslagslista").isVisible()).isFalse();
            assertThat(sida.locator("#tagg-chips .chip-tagg").last().textContent().trim()).isEqualTo("Favorit ×");
        }
    }

    /**
     * WINE-52: taggchipsen i vin-formular.html ska se ut precis som
     * filterchipsen i vinlistans verktygsrad - samma .chips/.chip-
     * grundstil (tema.css, ADR 0008/0019) och samma "text ×"-format.
     * Innan denna fix var den faktiska SKILLNADEN inte grundstilen (som
     * redan delades) utan att taggchippen renderade en webbläsarens
     * vanliga, synliga kryssruta - jämför datainnehåll/element-typ OCH
     * en riktig computed-style-jämförelse mellan de två sidorna, inte
     * bara att båda råkar ha klassen "chip".
     */
    @Test
    void skaGeTaggchippenSammaStilSomFilterchipsen() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(context);
            sida.locator("input[name=name]").fill("Barolo (chipjämförelse)");
            sida.locator("input[name=quantity]").fill("1");
            sida.locator("#tagg-input").fill("Favorit");
            sida.locator("#tagg-lagg-till").click();

            Locator taggChip = sida.locator("#tagg-chips .chip-tagg").first();
            assertThat((String) taggChip.evaluate("el => el.tagName")).isEqualTo("LABEL");
            assertThat(taggChip.textContent().trim()).isEqualTo("Favorit ×");

            // Kryssrutan är fortfarande den faktiska "ta bort"-kontrollen,
            // men ska vara visuellt dold - samma clip-teknik/förväntad yta
            // som filväljarens dolda <input type="file"> (jämför
            // skaDöljaFilinputenMenBehållaDenAnvändbar ovan).
            assertThat(ytaIPixlar(sida, "#tagg-chips .chip-tagg input[type=checkbox]")).isLessThanOrEqualTo(1);

            String bakgrund = computedStyle(sida, "#tagg-chips .chip-tagg", "background-color");
            String kantfärg = computedStyle(sida, "#tagg-chips .chip-tagg", "border-color");
            String radie = computedStyle(sida, "#tagg-chips .chip-tagg", "border-radius");
            String padding = computedStyle(sida, "#tagg-chips .chip-tagg", "padding");
            String typsnittsstorlek = computedStyle(sida, "#tagg-chips .chip-tagg", "font-size");

            sida.locator("input[name=name]").fill("Barolo (chipjämförelse) 2");
            sida.locator("input[name=quantity]").fill("2");
            sida.locator("button[type=submit]").last().click();
            sida.waitForURL(url("/"));

            // En riktig filterchip (samma .chips/.chip-grundstil) kräver
            // ett aktivt filter för att synas alls - ?tag=Favorit ger en.
            sida.navigate(url("/?tag=Favorit"));
            Locator filterChip = sida.locator(".chips > .chip").first();
            assertThat(filterChip.textContent().trim()).isEqualTo("Favorit ×");

            assertThat(computedStyle(sida, ".chips > .chip", "background-color")).isEqualTo(bakgrund);
            assertThat(computedStyle(sida, ".chips > .chip", "border-color")).isEqualTo(kantfärg);
            assertThat(computedStyle(sida, ".chips > .chip", "border-radius")).isEqualTo(radie);
            assertThat(computedStyle(sida, ".chips > .chip", "padding")).isEqualTo(padding);
            assertThat(computedStyle(sida, ".chips > .chip", "font-size")).isEqualTo(typsnittsstorlek);
        }
    }

    /** Ett enskilt CSS-egenskapsvärde, så som webbläsaren faktiskt beräknat det. */
    private String computedStyle(Page sida, String väljare, String egenskap) {
        return (String) sida.evaluate(
                "([väljare, egenskap]) => getComputedStyle(document.querySelector(väljare))"
                        + ".getPropertyValue(egenskap)",
                new Object[]{väljare, egenskap});
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
        return nyInloggadKontext(false);
    }

    /**
     * isMobile(true)/hasTouch(true) krävs för att över huvud taget spegla
     * en riktig mobil webbläsarkontext (inte bara en smal
     * setViewportSize) - se CLAUDE.md om varför en <meta name="viewport">
     * -medveten CSS-brytpunkt annars aldrig triggas i testet.
     */
    private BrowserContext nyInloggadKontext(boolean mobil) {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(mobil ? 375 : 1280, mobil ? 667 : 800)
                .setIsMobile(mobil)
                .setHasTouch(mobil));
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
