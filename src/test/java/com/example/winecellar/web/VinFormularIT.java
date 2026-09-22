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
import org.assertj.core.data.Offset;
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
     * <p>
     * UPPFÖLJNING (granskningsfynd EFTER merge, rapporterat av en riktig
     * användare - mvn verify OCH den ursprungliga varianten av just det
     * här testet var gröna trots att listan i praktiken inte gick att
     * se på mobil). Grundorsaken var att "Taggar" är sista fältet i sin
     * .kort, och .kort har overflow: hidden (tema.css, för att klippa
     * runda hörn) - vilket klippte bort förslagslistan. Testet
     * använder nu {@link #geometriskSynlig}, inte bara
     * {@code Locator.isVisible()}, som INTE upptäcker den klassen av
     * fel (ett klippt-av-förälder-element har fortfarande en normal,
     * icke-noll bounding box).
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
            assertThat(geometriskSynlig(sida, "#tagg-forslagslista li:nth-child(1)"))
                    .as("förslaget ska vara faktiskt synligt, inte bara ha en icke-noll bounding box")
                    .isTrue();

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
     * WINE-52 (granskningsfynd EFTER merge, rapporterat av en riktig
     * användare - "på dator får bara första träffen plats i
     * träfflistan"). Både mvn verify och de ursprungliga Playwright-
     * testerna var gröna trots detta, eftersom inget test hittills
     * hade mer än EN matchande tagg samtidigt - med bara en post i
     * listan syns aldrig skillnaden mellan "en post genuint synlig" och
     * "en post som RÅKAR vara den enda som inte hunnit klippas bort".
     * Grundorsaken: "Taggar" är sista fältet i sin .kort, och .kort har
     * overflow: hidden (tema.css, för att klippa runda hörn) - vilket
     * klippte bort förslagslistan (helt eller delvis, beroende på hur
     * nära kortets nederkant fältet råkade vara). Verifierat manuellt
     * mot en riktig körande app innan fixen (samma testscenario gav då
     * `geometriskSynlig() == false` för BÅDA posterna).
     */
    @Test
    void skaVisaFleraGenuintSynligaFörslagUtanAttKlippasAvKortetsOverflow() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page förstaVinet = öppnaFormuläret(context);
            förstaVinet.locator("input[name=name]").fill("Barolo (flera förslag)");
            förstaVinet.locator("input[name=quantity]").fill("1");
            förstaVinet.locator("#tagg-input").fill("Favorit");
            förstaVinet.locator("#tagg-lagg-till").click();
            förstaVinet.locator("#tagg-input").fill("Favoritvin");
            förstaVinet.locator("#tagg-lagg-till").click();
            förstaVinet.locator("button[type=submit]").last().click();
            förstaVinet.waitForURL(url("/"));

            Page sida = öppnaFormuläret(context);
            sida.locator("#tagg-input").fill("fav");

            Locator poster = sida.locator("#tagg-forslagslista li");
            assertThat(poster.count()).isEqualTo(2);
            assertThat(poster.allTextContents()).containsExactlyInAnyOrder("Favorit", "Favoritvin");

            for (int i = 1; i <= poster.count(); i++) {
                assertThat(geometriskSynlig(sida, "#tagg-forslagslista li:nth-child(" + i + ")"))
                        .as("post %d ska vara faktiskt synlig, inte klippt av kortets overflow: hidden", i)
                        .isTrue();
            }

            // Granskningsfynd (PR #35): JS sätter listans bredd till
            // exakt inputens rect.width - utan box-sizing: border-box
            // (se .tagg-forslagslista i <style>) hade den 1px-borderen
            // lagts UTANPÅ den bredden, och listan stuckit ut ~2px till
            // höger om inputens kant.
            double inputBredd = sida.locator("#tagg-input").boundingBox().width;
            double listBredd = sida.locator("#tagg-forslagslista").boundingBox().width;
            assertThat(listBredd).isCloseTo(inputBredd, Offset.offset(0.5));
        }
    }

    /**
     * WINE-52 (granskningsfynd, PR #35): window.innerHeight krymper INTE
     * när ett riktigt mobilt tangentbord öppnas i iOS Safari - bara
     * window.visualViewport.height gör det där, vilket är precis den
     * höjd flip-logiken i vin-formular.html (synligViewporthöjd()) läser
     * sedan den här fixen. Chromiums mobilemulering (isMobile(true), se
     * övriga WINE-52-tester) simulerar INTE ett riktigt mjukt
     * tangentbord - det finns inget sätt att trigga en äkta
     * visualViewport-krympning i ett automatiserat test. I stället
     * stubbas window.visualViewport direkt i sidan med ett konstgjort,
     * litet height-värde: testet bevisar därmed att koden FAKTISKT
     * läser window.visualViewport.height (inte bara window.innerHeight)
     * när den finns - ett regressionsskydd mot att koden tyst faller
     * tillbaka till det gamla, trasiga beteendet.
     * <p>
     * En hög viewport (1280×2000, se {@link #nyInloggadKontext(int, int)})
     * garanterar gott om utrymme nedanför enligt den RIKTIGA
     * window.innerHeight - om testet ändå ser en flip efter stubben
     * beror det bevisligen på stubben, inte på att "Taggar" redan ligger
     * nära kortets nederkant (som i de andra WINE-52-testerna).
     */
    @Test
    void skaAnvändaVisualViewportFörAttUpptäckaEttSimuleratTangentbord() {
        try (BrowserContext context = nyInloggadKontext(1280, 2000)) {
            Page förstaVinet = öppnaFormuläret(context);
            förstaVinet.locator("input[name=name]").fill("Barolo (visualViewport)");
            förstaVinet.locator("input[name=quantity]").fill("1");
            förstaVinet.locator("#tagg-input").fill("Favorit");
            förstaVinet.locator("#tagg-lagg-till").click();
            förstaVinet.locator("button[type=submit]").last().click();
            förstaVinet.waitForURL(url("/"));

            Page sida = öppnaFormuläret(context);
            Locator input = sida.locator("#tagg-input");
            Locator lista = sida.locator("#tagg-forslagslista");

            // Före stubben: gott om utrymme nedanför i den höga
            // viewporten - listan ska visas NEDANFÖR inputen som vanligt.
            input.fill("fav");
            assertThat(lista.boundingBox().y).isGreaterThanOrEqualTo(
                    input.boundingBox().y + input.boundingBox().height);

            // Stubbar window.visualViewport med en konstgjord, mycket
            // lägre höjd - simulerar att ett mjukt tangentbord täcker
            // nedre delen av skärmen, utan att faktiskt öppna ett.
            sida.evaluate("() => { window.visualViewport = "
                    + "{ height: 100, addEventListener: () => {}, removeEventListener: () => {} }; }");
            // Blur + refokusera för att trigga en ny positionering (samma
            // kodväg som en riktig visualViewport-resize-händelse hade
            // gjort) - inputens VÄRDE är oförändrat, så en ren fill()
            // riskerar att vara ett no-op i webbläsarens ögon.
            input.evaluate("el => el.blur()");
            input.click();

            // Efter stubben: listan ska nu vara flippad OVANFÖR inputen,
            // trots att den riktiga window.innerHeight fortfarande har
            // gott om utrymme kvar.
            assertThat(lista.boundingBox().y + lista.boundingBox().height)
                    .isLessThanOrEqualTo(input.boundingBox().y);
        }
    }

    /**
     * WINE-52 (buggrapport EFTER att PR #35 mergats - "på både dator och
     * mobil visar autocomplete-listan bara 1 tagg"). Listans höjd
     * begränsades bara av en FAST max-height (12rem), aldrig av det
     * faktiskt tillgängliga utrymmet: när varken ytan ovanför eller
     * nedanför rymde hela listan renderades den ändå i full höjd
     * nedanför och spillde ut under skärmkanten (eller under ett mobilt
     * tangentbord). `overflow-y: auto` räddade inte det - den scrollar
     * inuti listans egen 12rem-box, inte inom den synliga resten av
     * skärmen, så de avklippta posterna var helt oåtkomliga.
     * <p>
     * Tidigare Playwright-tester missade detta eftersom de alla kör i
     * RYMLIGA viewports där listan alltid fick plats. Det här testet kör
     * därför i en medvetet LÅG viewport (375×300) med fler matchande
     * taggar än vad som ryms, och verifierar både att hela listboxen
     * ligger innanför den synliga ytan OCH att de poster som inte får
     * plats faktiskt går att nå genom att scrolla inuti listan (inte
     * bara att de finns i DOM:en).
     */
    @Test
    void skaHållaHelaFörslagslistanInomEnLågViewport() {
        // Taggarna läggs upp i en rymlig viewport - att fylla i hela
        // vinformuläret i en 300px hög vy är onödigt skört, och det är
        // bara VISNINGEN av förslagen som ska testas i den låga vyn.
        // Egen tagg-prefix ("Sommar"), inte "Favorit"-taggarna som andra
        // tester i klassen räknar exakt antal träffar på - kontot delas
        // mellan alla testmetoder utan städning emellan.
        try (BrowserContext förberedelse = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(förberedelse);
            sida.locator("input[name=name]").fill("Barolo (låg viewport)");
            sida.locator("input[name=quantity]").fill("1");
            for (String tagg : new String[]{"Sommar", "Sommarvin", "Sommarfest",
                    "Sommarrött", "Sommarvitt", "Sommarbubbel"}) {
                sida.locator("#tagg-input").fill(tagg);
                sida.locator("#tagg-lagg-till").click();
            }
            sida.locator("button[type=submit]").last().click();
            sida.waitForURL(url("/"));
        }

        try (BrowserContext context = nyInloggadKontext(375, 300)) {
            Page sida = öppnaFormuläret(context);
            Locator lista = sida.locator("#tagg-forslagslista");
            sida.locator("#tagg-input").fill("sommar");

            assertThat(lista.isVisible()).isTrue();
            assertThat(lista.locator("li").count()).isEqualTo(6);

            // Scrolla så att fältet hamnar MITT i den låga vyn: då finns
            // det för lite plats både ovanför och nedanför (~150px var,
            // mot listans önskade 12rem = 192px), vilket är precis det
            // läge där enbart den fasta max-heighten inte räcker. Utan
            // scrollen hamnar fältet (tack vare Playwrights egen
            // scroll-into-view) i stället vid vyns kant, där ena sidan
            // råkar ha gott om plats och buggen inte syns. Sidans
            // scroll-lyssnare positionerar om listan automatiskt.
            sida.evaluate("() => { const f = document.getElementById('tagg-input');"
                    + " const r = f.getBoundingClientRect();"
                    + " window.scrollBy(0, r.top + r.height / 2 - window.innerHeight / 2); }");
            sida.waitForTimeout(100);

            // Hela listboxen ska ligga innanför den synliga ytan - det
            // var precis det den inte gjorde före fixen (sista posten
            // hamnade utanför skärmkanten i en 300px hög vy).
            double viewporthöjd = ((Number) sida.evaluate("() => window.innerHeight")).doubleValue();
            assertThat(lista.boundingBox().y)
                    .as("listans överkant ska ligga innanför viewporten")
                    .isGreaterThanOrEqualTo(0.0);
            assertThat(lista.boundingBox().y + lista.boundingBox().height)
                    .as("listans nederkant ska ligga innanför viewporten")
                    .isLessThanOrEqualTo(viewporthöjd);

            // Första posten ska synas direkt...
            assertThat(geometriskSynlig(sida, "#tagg-forslagslista li:first-child")).isTrue();

            // ...och de poster som inte får plats ska gå att nå genom att
            // scrolla INUTI listan (i stället för att ligga bortklippta
            // under skärmkanten, som före fixen).
            sida.evaluate("() => { const l = document.getElementById('tagg-forslagslista');"
                    + " l.scrollTop = l.scrollHeight; }");
            assertThat(geometriskSynlig(sida, "#tagg-forslagslista li:last-child"))
                    .as("sista posten ska bli synlig när listan scrollas till slutet")
                    .isTrue();
        }
    }

    /**
     * WINE-52 (granskningsfynd på PR #36): listan ligger i &lt;body&gt;
     * med position: fixed, och dess bredd sätts av
     * positioneraFörslag(). Vid den ALLRA FÖRSTA visningen mättes
     * höjden innan bredden hunnit sättas - listan var då fortfarande
     * shrink-to-fit mot viewporten, alltså bredare än inputen. En tagg
     * som ryms på EN rad vid den bredden men radbryts till två vid
     * inputens smalare bredd mättes därför en rad för kort, och listan
     * klipptes trots att det fanns gott om plats (det självläkte vid
     * nästa scroll/resize - men användaren som bara skriver och tittar
     * ser det klippta läget).
     * <p>
     * Testet mäter därför FÖRSTA öppningen, utan någon ompositionering
     * emellan, i en smal vy där skillnaden mellan listans auto-bredd och
     * inputens bredd är stor nog att flytta radbrytningen (uppmätt:
     * 296px auto mot 211px vid inputens bredd, dvs. en rad mot två).
     * <p>
     * Listan öppnas med EN ENDA tangenttryckning på ett tecken som bara
     * den långa taggen innehåller. Det är avgörande: {@code fill(...)}
     * öppnar listan TVÅ gånger (en gång för fokus, en gång för
     * inmatningen), och den andra gången mäts mot den bredd den första
     * hann sätta - vilket döljer felet helt. En riktig användare som
     * skriver det första matchande tecknet och tittar får däremot exakt
     * den enda öppning testet återskapar här.
     */
    @Test
    void skaIntePressaIhopListanVidFörstaVisningenAvEnLångTagg() {
        // "q" finns bara i den här taggen av alla som klassens tester
        // lägger upp - kontot delas mellan testmetoderna utan städning
        // emellan, så en tryckning på "q" ger garanterat exakt en träff.
        String långTagg = "Langtagg q for radbrytning i smal vy hos oss";
        try (BrowserContext förberedelse = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(förberedelse);
            sida.locator("input[name=name]").fill("Barolo (lång tagg)");
            sida.locator("input[name=quantity]").fill("1");
            sida.locator("#tagg-input").fill(långTagg);
            sida.locator("#tagg-lagg-till").click();
            sida.locator("button[type=submit]").last().click();
            sida.waitForURL(url("/"));
        }

        try (BrowserContext context = nyInloggadKontext(375, 667)) {
            Page sida = öppnaFormuläret(context);
            Locator lista = sida.locator("#tagg-forslagslista");
            Locator input = sida.locator("#tagg-input");
            input.click();
            assertThat(lista.isVisible()).as("tomt fält ska inte visa någon lista").isFalse();
            input.press("q");
            assertThat(lista.locator("li").allTextContents()).containsExactly(långTagg);

            // Ingen scroll/resize här emellan - just det är poängen.
            // scrollHeight > clientHeight betyder att listan har fått en
            // inre scroll, dvs. att den begränsats till mindre än sitt
            // innehåll trots att det finns gott om plats i vyn.
            assertThat(innerScrollIPixlar(sida))
                    .as("listan ska inte vara ihopklämd/inre-scrollande vid första visningen")
                    .isZero();

            // Bredden ska dessutom följa inputen redan från första
            // visningen (det är bredden som avgör radbrytningen).
            assertThat(lista.boundingBox().width)
                    .isCloseTo(sida.locator("#tagg-input").boundingBox().width, Offset.offset(0.5));
        }
    }

    /**
     * WINE-52 (granskningsfynd på PR #36): sedan listan begränsas till
     * det lediga utrymmet är den ofta lägre än sitt innehåll - men
     * tangentbordsmarkeringen (ArrowDown/ArrowUp) scrollades aldrig in i
     * den synliga randen. Användaren tryckte ArrowDown, såg ingenting
     * hända, tryckte Enter - och lade till en tagg hen aldrig sett.
     * "Scrolla inuti listan" (PR #36:s egen mekanism för de poster som
     * inte får plats) är ingen väg för den som navigerar med
     * tangentbordet.
     */
    @Test
    void skaScrollaFramDenTangentbordsmarkeradePostenILista() {
        String[] taggar = {"Zebra ett", "Zebra fem", "Zebra fyra",
                "Zebra sex", "Zebra tre", "Zebra tva"};
        try (BrowserContext förberedelse = nyInloggadKontext()) {
            Page sida = öppnaFormuläret(förberedelse);
            sida.locator("input[name=name]").fill("Barolo (tangentbordsscroll)");
            sida.locator("input[name=quantity]").fill("1");
            for (String tagg : taggar) {
                sida.locator("#tagg-input").fill(tagg);
                sida.locator("#tagg-lagg-till").click();
            }
            sida.locator("button[type=submit]").last().click();
            sida.waitForURL(url("/"));
        }

        try (BrowserContext context = nyInloggadKontext(375, 320)) {
            Page sida = öppnaFormuläret(context);
            Locator input = sida.locator("#tagg-input");
            Locator lista = sida.locator("#tagg-forslagslista");
            input.fill("zebra");
            assertThat(lista.locator("li").count()).isEqualTo(taggar.length);

            // Den låga vyn klämmer listan till mindre än sitt innehåll -
            // annars finns ingen markering som kan hamna utanför.
            assertThat(innerScrollIPixlar(sida))
                    .as("listan ska vara begränsad till mindre än sitt innehåll i den här vyn")
                    .isGreaterThan(0.0);

            // Stega hela vägen ner till sista posten.
            for (int i = 0; i < taggar.length; i++) {
                input.press("ArrowDown");
            }

            assertThat(sida.locator("#tagg-forslagslista li.aktiv").textContent())
                    .isEqualTo(taggar[taggar.length - 1]);
            assertThat(markeringenÄrInomListansSynligaRand(sida))
                    .as("den markerade posten ska scrollas fram, inte hamna utanför listans synliga rand")
                    .isTrue();
            assertThat(geometriskSynlig(sida, "#tagg-forslagslista li.aktiv"))
                    .as("den markerade posten ska dessutom vara faktiskt synlig på skärmen")
                    .isTrue();
        }
    }

    /**
     * Hur många pixlar av listans innehåll som ligger utanför dess
     * synliga box (0 = inget klipps, hela listan syns utan inre scroll).
     */
    private double innerScrollIPixlar(Page sida) {
        return ((Number) sida.evaluate("() => { const l = document.getElementById('tagg-forslagslista');"
                + " return Math.max(0, l.scrollHeight - l.clientHeight); }")).doubleValue();
    }

    /** Ligger den tangentbordsmarkerade posten innanför listans synliga rand? */
    private boolean markeringenÄrInomListansSynligaRand(Page sida) {
        return (Boolean) sida.evaluate("() => { const l = document.getElementById('tagg-forslagslista');"
                + " const aktiv = l.querySelector('li.aktiv');"
                + " if (!aktiv) return false;"
                + " const lr = l.getBoundingClientRect(); const ar = aktiv.getBoundingClientRect();"
                // 1px marginal för delpixelavrundning.
                + " return ar.top >= lr.top - 1 && ar.bottom <= lr.bottom + 1; }");
    }

    /**
     * WINE-52 (användarens uttryckliga önskemål efter att PR #35
     * mergats): "listan visas så fort fältet får fokus, medan min tanke
     * var 'autocomplete', dvs att alternativ inte visades förrän
     * användaren börjat skriva något som liknar en befintlig tagg".
     * Förslagen ska alltså bara dyka upp när fältet faktiskt innehåller
     * något - inte som en komplett tagglista direkt vid fokus (vilket
     * dessutom gjorde höjdproblemet ovan värst möjligt: alla taggar på
     * en gång, på den minsta möjliga ytan).
     */
    @Test
    void skaIntePresenteraFörslagFörränAnvändarenBörjatSkriva() {
        try (BrowserContext context = nyInloggadKontext()) {
            Page förstaVinet = öppnaFormuläret(context);
            förstaVinet.locator("input[name=name]").fill("Barolo (skriv först)");
            förstaVinet.locator("input[name=quantity]").fill("1");
            förstaVinet.locator("#tagg-input").fill("Vardag");
            förstaVinet.locator("#tagg-lagg-till").click();
            förstaVinet.locator("button[type=submit]").last().click();
            förstaVinet.waitForURL(url("/"));

            Page sida = öppnaFormuläret(context);
            Locator input = sida.locator("#tagg-input");
            Locator lista = sida.locator("#tagg-forslagslista");

            // Fokus på ett TOMT fält ska inte ge några förslag alls.
            input.click();
            assertThat(input.inputValue()).isEmpty();
            assertThat(lista.isVisible())
                    .as("inga förslag förrän användaren skrivit något")
                    .isFalse();

            // Så fort något skrivs som liknar en befintlig tagg visas de.
            input.fill("vard");
            assertThat(lista.isVisible()).isTrue();
            assertThat(lista.locator("li").allTextContents()).contains("Vardag");

            // ...och töms fältet igen försvinner de.
            input.fill("");
            assertThat(lista.isVisible())
                    .as("ett tomt fält ska dölja listan igen, inte visa alla taggar")
                    .isFalse();
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

    /**
     * Kollar att elementet FAKTISKT är målat och synligt på den plats
     * det själv rapporterar (getBoundingClientRect) - till skillnad
     * från {@code Locator.isVisible()}, som bara kollar CSS display/
     * visibility och en icke-noll yta. Ett element kan ha en fullt
     * normal, icke-noll bounding box men ändå vara helt osynligt för en
     * riktig användare om en förälder klipper bort det
     * (overflow: hidden) - exakt den klassen av bugg som WINE-52:s
     * taggförslag hade (granskningsfynd EFTER merge, hittat av en
     * riktig användare - varken mvn verify eller de ursprungliga
     * Playwright-testerna fångade det). document.elementFromPoint(...)
     * frågar webbläsaren vad som FAKTISKT ritas på en given
     * skärmpunkt - precis det isVisible() inte gör.
     */
    private boolean geometriskSynlig(Page sida, String väljare) {
        return (Boolean) sida.evaluate(
                "väljare => { const el = document.querySelector(väljare); if (!el) return false;"
                        + " const r = el.getBoundingClientRect();"
                        + " if (r.width === 0 || r.height === 0) return false;"
                        + " const cx = r.left + r.width / 2; const cy = r.top + r.height / 2;"
                        + " const träff = document.elementFromPoint(cx, cy);"
                        + " return !!träff && (träff === el || el.contains(träff) || träff.contains(el)); }",
                väljare);
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
        return nyInloggadKontext(new Browser.NewContextOptions()
                .setViewportSize(mobil ? 375 : 1280, mobil ? 667 : 800)
                .setIsMobile(mobil)
                .setHasTouch(mobil));
    }

    /**
     * Explicit viewport-storlek, utan mobilemulering - för de tester som
     * behöver styra utrymmet runt taggfältet exakt i stället för att ta
     * klassens vanliga mått. Används åt båda hållen: medvetet RYMLIGT
     * (så att en flip bevisligen beror på det testet framkallar, inte på
     * att "Taggar" råkar ligga nära kortets nederkant) och medvetet
     * TRÅNGT (så att listan måste begränsas till det lediga utrymmet).
     */
    private BrowserContext nyInloggadKontext(int bredd, int höjd) {
        return nyInloggadKontext(new Browser.NewContextOptions().setViewportSize(bredd, höjd));
    }

    private BrowserContext nyInloggadKontext(Browser.NewContextOptions options) {
        BrowserContext context = browser.newContext(options);
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
