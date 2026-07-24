package com.example.winecellar.web;

import com.example.winecellar.application.InterpretedLabel;
import com.example.winecellar.application.LabelInterpreter;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.FilePayload;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @WebMvcTest/MockMvc kör inget JavaScript och kan inte verifiera att
 * etikett-JS:en (nedskalning + automatiskt inskick, "tolkat"-markeringens
 * släckning vid redigering) faktiskt fungerar i en riktig webbläsare -
 * se WINE-5 och vin-formular.html. LabelInterpreter mockas (inte den
 * riktiga Anthropic-adaptern) - precis som Cucumber-scenarierna använder
 * FakeLabelInterpreter, se docs/adr/0012.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class LabelScanFormIT {

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

    @MockBean
    private LabelInterpreter labelInterpreter;

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

    // En riktig, avkodningsbar 1x1-PNG (samma testbild som
    // WineRowWriterTest använder) - JS:ens Canvas-nedskalning kräver att
    // webbläsaren faktiskt kan avkoda bilden, inte bara godtyckliga bytes.
    private static final byte[] EN_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Test
    void skaVisaEttFörifylltUtkastMedTolkadeFältMarkerade() {
        when(labelInterpreter.interpret(any(), any())).thenReturn(Optional.of(
                new InterpretedLabel("Barolo", "Pio Cesare", 2018, "Italien", "Piemonte")));

        try (BrowserContext context = nyKontext()) {
            Page page = öppnaNyttVinFormulär(context);
            skannaEtikett(page);

            assertThat(page.locator("input[name=name]").inputValue()).isEqualTo("Barolo");
            assertThat(page.locator("input[name=producer]").inputValue()).isEqualTo("Pio Cesare");
            assertThat(page.locator("input[name=vintage]").inputValue()).isEqualTo("2018");
            assertThat(page.locator("input[name=country]").inputValue()).isEqualTo("Italien");
            assertThat(page.locator("input[name=region]").inputValue()).isEqualTo("Piemonte");

            assertThat(harTolkatMarkering(page, "name")).isTrue();
            assertThat(harTolkatMarkering(page, "producer")).isTrue();
            assertThat(harTolkatMarkering(page, "vintage")).isTrue();
        }
    }

    @Test
    void skaVisaAnalyserarStatusMedanEtikettenBearbetas() {
        // Konstgjord fördröjning (WINE-8) - annars kan mock-svaret komma
        // tillbaka och sidan navigera bort SÅ snabbt (en 1x1-testbild, ingen
        // riktig nätverksfördröjning) att statusraden hinner försvinna innan
        // assertionen nedan hinner läsa den. "Analyserar etikett..." sätts
        // synkront direkt när filen väljs (se vin-formular.html), innan
        // Canvas-nedskalningen eller nätverksanropet ens börjar - fördröjningen
        // simulerar bara den riktiga LLM-anropstiden så testet får ett
        // pålitligt fönster att observera statusraden i.
        when(labelInterpreter.interpret(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(800);
            return Optional.of(new InterpretedLabel("Barolo", "Pio Cesare", 2018, "Italien", "Piemonte"));
        });

        try (BrowserContext context = nyKontext()) {
            Page page = öppnaNyttVinFormulär(context);

            page.locator("#etikett-input").setInputFiles(
                    new FilePayload("etikett.png", "image/png", EN_PIXEL_PNG));

            assertThat(page.locator("#etikett-status").textContent()).isEqualTo("Analyserar etikett...");

            page.waitForURL("**/wines/tolka-etikett");
        }
    }

    @Test
    void skaSläckaMarkeringenPåEttFältNärDetRedigeras() {
        when(labelInterpreter.interpret(any(), any())).thenReturn(Optional.of(
                new InterpretedLabel("Barolo", "Pio Cesare", 2018, "Italien", "Piemonte")));

        try (BrowserContext context = nyKontext()) {
            Page page = öppnaNyttVinFormulär(context);
            skannaEtikett(page);

            page.locator("input[name=vintage]").fill("2019");

            assertThat(harTolkatMarkering(page, "vintage")).isFalse();
            assertThat(harTolkatMarkering(page, "name")).isTrue();
        }
    }

    private void skannaEtikett(Page page) {
        page.locator("#etikett-input").setInputFiles(
                new FilePayload("etikett.png", "image/png", EN_PIXEL_PNG));
        page.waitForURL("**/wines/tolka-etikett");
    }

    private boolean harTolkatMarkering(Page page, String fältnamn) {
        String klass = page.locator("[name=" + fältnamn + "]").getAttribute("class");
        return klass != null && klass.contains("tolkat-falt");
    }

    /**
     * WINE-12: formulärinloggning med session ersatte HTTP Basic (samma
     * ändring som WineListResponsiveIT) - se den för det fullständiga
     * resonemanget.
     */
    private BrowserContext nyKontext() {
        BrowserContext context = browser.newContext();
        Page inloggningssida = context.newPage();
        inloggningssida.navigate("http://localhost:" + port + "/login");
        inloggningssida.locator("#username").fill("admin");
        inloggningssida.locator("#password").fill("admin");
        inloggningssida.locator("button[type=submit]").click();
        inloggningssida.waitForLoadState();
        inloggningssida.close();
        return context;
    }

    private Page öppnaNyttVinFormulär(BrowserContext context) {
        Page page = context.newPage();
        page.navigate("http://localhost:" + port + "/wines/nytt");
        return page;
    }
}
