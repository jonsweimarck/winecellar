package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationService;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.FilePayload;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WINE-26: en fullständig, riktig webbläsarrundtur genom hela
 * import-/exportflödet (WINE-20 till WINE-25, se ADR 0014) - inte bara
 * MockMvc-lagret (`ExportControllerTest`/`ImportControllerTest` testar
 * webblagrets orkestrering isolerat, men ingen av dem kör en riktig
 * webbläsare eller en riktig Postgres samtidigt). Ett konto (A) lägger
 * till ett vin med bild, exporterar både xlsx och bildzip, ett HELT
 * ANNAT konto (B, tomt) importerar tillbaka - verifierar att rundtrippen
 * är bildmedveten (en bild kommer med och kopplas till rätt vin - INTE
 * byte-identisk, se kommentaren vid bildjämförelsen längre ner för
 * varför) och att importen fungerar över kontogränsen (B har inget att
 * jämföra dubbletter mot, så raden ska bli en ren, ny import).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ImportExportFlowIT {

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
    private RegistrationService registrationService;

    private static final String KONTO_A_ANVÄNDARNAMN = "importExportFlowA";
    private static final String KONTO_B_ANVÄNDARNAMN = "importExportFlowB";
    private static final String KONTO_TRANSPARENS_A_ANVÄNDARNAMN = "importExportFlowTransparentA";
    private static final String KONTO_TRANSPARENS_B_ANVÄNDARNAMN = "importExportFlowTransparentB";
    private static final String LÖSENORD = "testlösenord123";

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

    // Samma kända, avkodningsbara 1x1-PNG som används på flera andra
    // ställen i testsviten (t.ex. LabelScanFormIT) - webbläsaren måste
    // faktiskt kunna avkoda bilden för att Canvas-nedskalningen i
    // import.html ska fungera.
    private static final byte[] EN_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    // 1x1 RGBA-PNG med en halvtransparent röd pixel (alfa 128 av 255) - till
    // skillnad från EN_PIXEL_PNG ovan (grayscale+alfa, men den enda pixeln är
    // faktiskt helt ogenomskinlig, alfa 255) behöver WINE-29-testet en bild
    // som FAKTISKT har transparens för att träffa den nya kodvägen i
    // import.html.
    private static final byte[] HALVTRANSPARENT_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DQAAAEgQGALFXOsAAAAABJRU5ErkJggg==");

    @Test
    void skaExporteraOchImporteraEttVinMedBildTillEttAnnatKontoIdentiskt(@TempDir Path tempDir) throws Exception {
        registrationService.register(KONTO_A_ANVÄNDARNAMN, LÖSENORD);
        registrationService.register(KONTO_B_ANVÄNDARNAMN, LÖSENORD);

        try (BrowserContext kontoA = nyKontext()) {
            loggaIn(kontoA, KONTO_A_ANVÄNDARNAMN);
            läggTillVinMedBild(kontoA);

            Path xlsxFil = ladda(kontoA, "/export/xlsx", tempDir.resolve("vinlista.xlsx"));
            Path zipFil = ladda(kontoA, "/export/bilder.zip", tempDir.resolve("bilder.zip"));
            Path bildmapp = packaUppIEgenMapp(zipFil, tempDir);

            try (BrowserContext kontoB = nyKontext()) {
                loggaIn(kontoB, KONTO_B_ANVÄNDARNAMN);
                Page sida = kontoB.newPage();
                sida.navigate("http://localhost:" + port + "/import");

                sida.locator("#fil-input").setInputFiles(xlsxFil);
                // webkitdirectory-inputen kräver en MAPPSÖKVÄG (inte enskilda
                // filsökvägar) - Playwright laddar då upp alla filer i mappen,
                // precis som en riktig mappväljare skulle göra.
                sida.locator("#bilder-input").setInputFiles(bildmapp);
                // Canvas-nedskalningen (import.html) körs asynkront på
                // 'change' - vänta tills den är klar (skickaknappen
                // återaktiveras) innan vi klickar, annars skickas
                // originalfilen innan JS:en hunnit bli klar.
                sida.locator("#import-submit:not([disabled])").waitFor();
                sida.locator("#import-submit").click();
                sida.waitForURL("**/import");

                assertThat(sida.locator(".sammanfattning").first().textContent()).contains("Nya viner");
                assertThat(sida.locator("dd").nth(4).textContent()).isEqualTo("1");

                sida.locator("button:has-text(\"Importera\")").click();
                sida.waitForURL("**/import");
                assertThat(sida.locator(".sammanfattning").first().textContent())
                        .contains("Importerade som nya viner");

                sida.navigate("http://localhost:" + port + "/");
                assertThat(sida.locator("body").textContent()).contains("Barolo").contains("Pio Cesare");

                // INTE byte-identisk med originalet - klientsidans Canvas-
                // nedskalning (import.html, WINE-24) skriver ALLTID om bilden
                // till JPEG (oavsett ursprungsformat), en medveten avvägning
                // för att hålla nere storleken på en bulkimports bildmapp.
                // Den byte-exakta rundtrippen testas redan (utan JS, via
                // curl) i WINE-23s manuella verifiering av exportsidan -
                // den här testet verifierar istället att en bild över huvud
                // taget kommer fram, kopplad till rätt vin, i rätt format.
                String bildUrl = sida.locator("img[src*='/bild']").first().getAttribute("src");
                APIResponse bildSvar = kontoB.request().get("http://localhost:" + port + bildUrl);
                assertThat(bildSvar.ok()).isTrue();
                assertThat(bildSvar.headers().get("content-type")).isEqualTo("image/jpeg");
                assertThat(bildSvar.body()).isNotEmpty();
            }
        }
    }

    /**
     * WINE-29: en bild med transparent bakgrund tappade sin transparens helt
     * vid bulkimport, eftersom import.html:s Canvas-nedskalning ALLTID skrev
     * om innehållet till JPEG (som saknar alfakanal) oavsett ursprungsformat -
     * se tillägget i ADR 0015. Samma rundtur som testet ovan (konto A sparar
     * ett vin med bild, exporterar, konto B bulk-importerar tillbaka), men med
     * en FAKTISKT transparent testbild och en assertion på att alfakanalen
     * fortfarande finns kvar efter rundtrippen - inte bara att en bild kommer
     * fram.
     */
    @Test
    void skaBevaraTransparensVidBulkimportAvEnBildMedGenomskinligBakgrund(@TempDir Path tempDir) throws Exception {
        registrationService.register(KONTO_TRANSPARENS_A_ANVÄNDARNAMN, LÖSENORD);
        registrationService.register(KONTO_TRANSPARENS_B_ANVÄNDARNAMN, LÖSENORD);

        try (BrowserContext kontoA = nyKontext()) {
            loggaIn(kontoA, KONTO_TRANSPARENS_A_ANVÄNDARNAMN);
            läggTillVinMedBild(kontoA, HALVTRANSPARENT_PIXEL_PNG, "etikett.png", "image/png");

            Path xlsxFil = ladda(kontoA, "/export/xlsx", tempDir.resolve("vinlista.xlsx"));
            Path zipFil = ladda(kontoA, "/export/bilder.zip", tempDir.resolve("bilder.zip"));
            Path bildmapp = packaUppIEgenMapp(zipFil, tempDir);

            try (BrowserContext kontoB = nyKontext()) {
                loggaIn(kontoB, KONTO_TRANSPARENS_B_ANVÄNDARNAMN);
                Page sida = kontoB.newPage();
                sida.navigate("http://localhost:" + port + "/import");

                sida.locator("#fil-input").setInputFiles(xlsxFil);
                sida.locator("#bilder-input").setInputFiles(bildmapp);
                sida.locator("#import-submit:not([disabled])").waitFor();
                sida.locator("#import-submit").click();
                sida.waitForURL("**/import");

                sida.locator("button:has-text(\"Importera\")").click();
                sida.waitForURL("**/import");

                sida.navigate("http://localhost:" + port + "/");
                String bildUrl = sida.locator("img[src*='/bild']").first().getAttribute("src");

                // Content-Type ska INTE vara image/jpeg (JPEG saknar alfakanal -
                // om vi ser jpeg här har transparensen redan gått förlorad).
                APIResponse bildSvar = kontoB.request().get("http://localhost:" + port + bildUrl);
                assertThat(bildSvar.headers().get("content-type")).isNotEqualTo("image/jpeg");

                // Avkoda den FAKTISKA responsen i webbläsaren själv (inte via
                // Java/ImageIO - JVM:en saknar inbyggt WebP-stöd, som är precis
                // det format Chromiums canvas.toBlob väljer för transparenta
                // bilder) och läs av alfavärdet för pixeln efter hela
                // rundtrippen (uppladdning → nedskalning → export → bulkimport
                // → visning).
                Object alfavärde = sida.evaluate("async (url) => {"
                        + "const svar = await fetch(url);"
                        + "const blob = await svar.blob();"
                        + "const bitmap = await createImageBitmap(blob);"
                        + "const duk = new OffscreenCanvas(bitmap.width, bitmap.height);"
                        + "const kontext = duk.getContext('2d');"
                        + "kontext.drawImage(bitmap, 0, 0);"
                        + "return kontext.getImageData(0, 0, 1, 1).data[3];"
                        + "}", bildUrl);

                assertThat(((Number) alfavärde).intValue()).isLessThan(255);
            }
        }
    }

    private void läggTillVinMedBild(BrowserContext context) throws IOException {
        läggTillVinMedBild(context, EN_PIXEL_PNG, "etikett.png", "image/png");
    }

    private void läggTillVinMedBild(BrowserContext context, byte[] bildBytes, String filnamn, String mimeTyp)
            throws IOException {
        Page sida = context.newPage();
        sida.navigate("http://localhost:" + port + "/wines/nytt");

        sida.locator("input[name=name]").fill("Barolo");
        sida.locator("select[name=wineType]").selectOption("RED");
        sida.locator("input[name=producer]").fill("Pio Cesare");
        sida.locator("input[name=country]").fill("Italien");
        sida.locator("input[name=vintage]").fill("2018");
        sida.locator("input[name=quantity]").fill("3");
        sida.locator("input[name=location]").fill("Låda 1");
        // Två fält heter "bild" (den dolda etikettskanningsfältet överst,
        // och det vanliga bildfältet i huvudformuläret) - nth(1) är det
        // vanliga, synliga fältet.
        sida.locator("input[name=bild]").nth(1).setInputFiles(
                new FilePayload(filnamn, mimeTyp, bildBytes));

        sida.locator("button:has-text(\"Lägg till\")").click();
        sida.waitForURL("http://localhost:" + port + "/");
        sida.close();
    }

    private Path ladda(BrowserContext context, String path, Path måldestination) {
        Page sida = context.newPage();
        sida.navigate("http://localhost:" + port + "/");
        Download nedladdning = sida.waitForDownload(() -> sida.locator("a[href='" + path + "']").click());
        nedladdning.saveAs(måldestination);
        sida.close();
        return måldestination;
    }

    /**
     * Packar upp den enda bildfilen i zip:en till en EGEN mapp, med EXAKT
     * samma filnamn som zip-posten (namnkonventionen, WINE-21/23) -
     * avgörande för att `ImageMatcher` ska hitta den igen vid importen.
     * Måste vara en egen mapp (inte bara en fil) - Playwrights
     * `setInputFiles` på en `webkitdirectory`-input kräver en mappsökväg.
     */
    private Path packaUppIEgenMapp(Path zipFil, Path tempDir) throws IOException {
        Path bildmapp = Files.createDirectory(tempDir.resolve("bildmapp"));
        try (ZipFile zip = new ZipFile(zipFil.toFile())) {
            Enumeration<? extends ZipEntry> poster = zip.entries();
            ZipEntry post = poster.nextElement();
            try (InputStream in = zip.getInputStream(post)) {
                Files.copy(in, bildmapp.resolve(post.getName()));
            }
        }
        return bildmapp;
    }

    private BrowserContext nyKontext() {
        return browser.newContext();
    }

    private void loggaIn(BrowserContext context, String användarnamn) {
        Page inloggningssida = context.newPage();
        inloggningssida.navigate("http://localhost:" + port + "/login");
        inloggningssida.locator("#username").fill(användarnamn);
        inloggningssida.locator("#password").fill(LÖSENORD);
        inloggningssida.locator("button[type=submit]").click();
        inloggningssida.waitForLoadState();
        inloggningssida.close();
    }
}
