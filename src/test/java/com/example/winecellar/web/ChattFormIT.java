package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.application.WineChatAssistant;
import com.example.winecellar.support.SharedPostgres;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @WebMvcTest/MockMvc kör inget JavaScript och kan inte verifiera att
 * väntefeedback-JS:en (WINE-49, chatt-lista.html/chatt.html) faktiskt syns
 * i en riktig webbläsare medan svaret väntas in - samma resonemang som
 * LabelScanFormIT (WINE-8). WineChatAssistant mockas (inte den riktiga
 * Anthropic-adaptern), precis som chatta-om-viner.feature använder
 * FakeWineChatAssistant.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ChattFormIT extends SharedPostgres {

    @LocalServerPort
    private int port;

    @MockBean
    private WineChatAssistant wineChatAssistant;

    @Autowired
    private RegistrationService registrationService;

    private static final String TESTKONTO_ANVÄNDARNAMN = "chattFormTest";
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

    /**
     * Samma mönster som LabelScanFormIT/WineListResponsiveIT (WINE-15):
     * inget hårdkodat konto kvar - registrerar ett riktigt testkonto,
     * idempotent mellan testmetoder i samma klassomgång.
     */
    @BeforeEach
    void säkerställTestkonto() {
        registrationService.register(TESTKONTO_ANVÄNDARNAMN, TESTKONTO_LÖSENORD);
    }

    @Test
    void skaVisaVäntestatusMedanEnNyKonversationSkapas() {
        fördröjtSvar("Ett vinförslag");

        try (BrowserContext context = nyKontext()) {
            Page page = context.newPage();
            page.navigate("http://localhost:" + port + "/chatt");

            page.locator(".ny-konversation textarea[name=message]").fill("Vilket vin passar till fisk?");
            klickaUtanAttVäntaPåNavigering(page.locator(".ny-konversation button[type=submit]"));

            // Statustexten sätts synkront i submit-handlern (se
            // chatt-lista.html), innan sidan navigerar bort - fördröjningen
            // ovan simulerar bara den riktiga LLM-anropstiden så testet får
            // ett pålitligt fönster att observera den i. noWaitAfter behövs
            // eftersom Playwrights click() annars väntar in HELA
            // navigeringen (inklusive vårt konstgjorda dröjsmål) innan
            // metoden ens returnerar - för sent för att observera statusen.
            // Statusen och knappens disabled-läge läses i EN enda
            // evaluate-avläsning, inte två separata Locator-anrop - annars
            // hinner navigeringen (redirecten efter dröjsmålet) i sällsynta
            // fall gå igenom mellan de två avläsningarna och ge ett falskt
            // negativt resultat på den andra.
            assertThat(läsVäntestatus(page, "ny-konversation-status", ".ny-konversation button[type=submit]"))
                    .isEqualTo(Map.of("text", "Skickar meddelande, väntar på svar...", "disabled", true));

            page.waitForURL(Pattern.compile(".*/chatt/\\d+"));
        }
    }

    @Test
    void skaVisaVäntestatusMedanSvarVäntasPåEttMeddelande() {
        fördröjtSvar("Första svaret");

        try (BrowserContext context = nyKontext()) {
            Page page = context.newPage();
            page.navigate("http://localhost:" + port + "/chatt");
            page.locator(".ny-konversation textarea[name=message]").fill("Vilket vin passar till fisk?");
            page.locator(".ny-konversation button[type=submit]").click();
            page.waitForURL(Pattern.compile(".*/chatt/\\d+"));

            fördröjtSvar("Andra svaret");
            page.locator(".nytt-meddelande textarea[name=message]").fill("Några fler förslag?");
            klickaUtanAttVäntaPåNavigering(page.locator(".nytt-meddelande button[type=submit]"));

            assertThat(läsVäntestatus(page, "nytt-meddelande-status", ".nytt-meddelande button[type=submit]"))
                    .isEqualTo(Map.of("text", "Skickar meddelande, väntar på svar...", "disabled", true));
        }
    }

    private void klickaUtanAttVäntaPåNavigering(Locator knapp) {
        knapp.click(new Locator.ClickOptions().setNoWaitAfter(true));
    }

    /**
     * Läser statustext och knappens disabled-läge i EN evaluate-avläsning
     * (se kommentaren vid anropsplatsen) - ett atomärt ögonblicksvärde,
     * inte två separata Locator-anrop som var för sig kan hamna på var sin
     * sida om redirecten.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> läsVäntestatus(Page page, String statusId, String knappSelector) {
        return (Map<String, Object>) page.evaluate(
                "() => ({" +
                        "text: document.getElementById('" + statusId + "').textContent," +
                        "disabled: document.querySelector('" + knappSelector + "').disabled" +
                        "})");
    }

    private void fördröjtSvar(String svar) {
        when(wineChatAssistant.reply(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(800);
            return Optional.of(svar);
        });
    }

    /** WINE-12: formulärinloggning med session - se LabelScanFormIT. */
    private BrowserContext nyKontext() {
        BrowserContext context = browser.newContext();
        Page inloggningssida = context.newPage();
        inloggningssida.navigate("http://localhost:" + port + "/login");
        inloggningssida.locator("#username").fill(TESTKONTO_ANVÄNDARNAMN);
        inloggningssida.locator("#password").fill(TESTKONTO_LÖSENORD);
        inloggningssida.locator("button[type=submit]").click();
        inloggningssida.waitForLoadState();
        inloggningssida.close();
        return context;
    }
}
