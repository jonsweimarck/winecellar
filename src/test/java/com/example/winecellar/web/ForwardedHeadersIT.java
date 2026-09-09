package com.example.winecellar.web;

import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * WINE-43 (kodgranskningsfynd): verifierar mot en riktig inbäddad
 * servletcontainer (inte @WebMvcTest/MockMvc, som inte bootar
 * ForwardedHeaderFilter-registreringen) vad {@code
 * server.forward-headers-strategy: framework} FAKTISKT åstadkommer när en
 * {@code X-Forwarded-Proto: https}-header följer med, i default-profilen
 * (ingen {@code SPRING_PROFILES_ACTIVE} satt - motsvarar lokal utveckling).
 *
 * <p><b>Två skilda, empiriskt bekräftade resultat, inte ett:</b>
 * <ul>
 *   <li>Remember-me-cookien (skriven av Spring Securitys eget applikations-
 *   lager, som läser requestens `isSecure()` via samma request-wrapper som
 *   ForwardedHeaderFilter satte upp) FÅR `Secure` satt korrekt - verifierat
 *   av {@code skaSättaSäkerRememberMeCookieMedForwardedHttpsHeader}.</li>
 *   <li>Den vanliga sessionscookien (JSESSIONID) FÅR DET INTE i den här
 *   (default-)profilen - Tomcats egen sessionshantering skapar och skriver
 *   den cookien direkt via servletcontainerns interna request-objekt,
 *   INNAN/UTANFÖR ForwardedHeaderFilter-wrappern som resten av requesten
 *   ser. Detta motsäger den ursprungliga WINE-43-dokumentationens
 *   påstående att BÅDA cookies skyddas - se
 *   `skaInteSättaSäkerSessionscookieTrotsForwardedHttpsHeader`, som
 *   medvetet dokumenterar den faktiska begränsningen istället för att
 *   dölja den.</li>
 * </ul>
 *
 * <p>Löst för produktion via en separat produktionsprofil, se
 * {@link ForwardedHeadersProdProfileIT} - sessionscookien ska FÖRBLI osäker
 * här i default-profilen, annars går lokal HTTP-utveckling sönder (se
 * ADR 0020 och CLAUDE.md, Kända fällor).
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = "winecellar.remember-me.key=forwarded-headers-test-nyckel")
class ForwardedHeadersIT extends SharedPostgres {

    private static final String ANVÄNDARNAMN = "forwardedHeadersTestUser";
    private static final String LÖSENORD = "testlösenord123";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RegistrationService registrationService;

    @Test
    void skaSättaSäkerRememberMeCookieMedForwardedHttpsHeader() {
        String rememberMeCookie = loggaInOchHämtaRememberMeCookie(true);

        assertThat(rememberMeCookie).isNotNull();
        assertThat(rememberMeCookie.toLowerCase()).contains("secure");
    }

    @Test
    void skaInteSättaSäkerRememberMeCookieUtanForwardedHttpsHeader() {
        String rememberMeCookie = loggaInOchHämtaRememberMeCookie(false);

        assertThat(rememberMeCookie).isNotNull();
        assertThat(rememberMeCookie.toLowerCase()).doesNotContain("secure");
    }

    /**
     * Dokumenterar en verifierad BEGRÄNSNING (inte ett önskat beteende):
     * till skillnad från remember-me-cookien ovan förblir sessionscookien
     * osäker även med `X-Forwarded-Proto: https` satt, eftersom
     * "framework"-strategin bara påverkar requesten så som Spring-lagret
     * ser den, inte Tomcats egen, tidigare sessionscookie-generering. Se
     * klassens Javadoc.
     */
    @Test
    void skaInteSättaSäkerSessionscookieTrotsForwardedHttpsHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-Proto", "https");
        ResponseEntity<String> svar = restTemplate.exchange(
                "/login", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(svar.getStatusCode()).isEqualTo(HttpStatus.OK);
        String sessionscookie = cookieMedPrefix(svar, "JSESSIONID=");
        assertThat(sessionscookie).isNotNull();
        assertThat(sessionscookie.toLowerCase()).doesNotContain("secure");
    }

    private String loggaInOchHämtaRememberMeCookie(boolean medForwardedHttpsHeader) {
        registrationService.register(ANVÄNDARNAMN, LÖSENORD);

        HttpHeaders getHeaders = new HttpHeaders();
        if (medForwardedHttpsHeader) {
            getHeaders.set("X-Forwarded-Proto", "https");
        }
        ResponseEntity<String> inloggningssida = restTemplate.exchange(
                "/login", HttpMethod.GET, new HttpEntity<>(getHeaders), String.class);
        String csrfToken = extraheraCsrfToken(inloggningssida.getBody());
        String sessionscookie = cookieMedPrefix(inloggningssida, "JSESSIONID=");
        assertThat(sessionscookie).isNotNull();

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", ANVÄNDARNAMN);
        form.add("password", LÖSENORD);
        form.add("remember-me", "on");
        form.add("_csrf", csrfToken);

        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (medForwardedHttpsHeader) {
            postHeaders.set("X-Forwarded-Proto", "https");
        }
        postHeaders.set(HttpHeaders.COOKIE, sessionscookie.split(";")[0]);

        ResponseEntity<String> inloggning = restTemplate.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(form, postHeaders), String.class);
        assertThat(inloggning.getStatusCode()).isEqualTo(HttpStatus.FOUND);

        return cookieMedPrefix(inloggning, "remember-me=");
    }

    private static String extraheraCsrfToken(String loginHtml) {
        return loginHtml.replaceAll("(?s).*name=\"_csrf\" value=\"([^\"]+)\".*", "$1");
    }

    private static String cookieMedPrefix(ResponseEntity<String> svar, String prefix) {
        List<String> setCookieHeaders = svar.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotNull();
        return setCookieHeaders.stream()
                .filter(cookie -> cookie.startsWith(prefix))
                .findFirst()
                .orElse(null);
    }
}
