package com.example.winecellar.web;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.HttpCredentials;
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
    void läggTillEttVin() {
        wineService.save(Wine.builder()
                .name("Barolo").wineType(WineType.RED).producer("Pio Cesare").country("Italien")
                .vintage(2018).quantity(3).location("Låda 1")
                .build());
    }

    @AfterEach
    void tömKällaren() {
        wineService.listWines().forEach(vin -> wineService.removeWine(vin.id()));
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

    private BrowserContext nyKontext(int bredd, int höjd, boolean mobil) {
        return browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(bredd, höjd)
                .setIsMobile(mobil)
                .setHasTouch(mobil)
                .setHttpCredentials(new HttpCredentials("admin", "admin")));
    }

    private Page öppnaVinkällaren(BrowserContext context) {
        Page page = context.newPage();
        page.navigate("http://localhost:" + port + "/");
        return page;
    }
}
