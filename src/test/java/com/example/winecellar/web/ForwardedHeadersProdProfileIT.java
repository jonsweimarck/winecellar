package com.example.winecellar.web;

import com.example.winecellar.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * WINE-43 (uppföljning efter eskalering till arkitekt/användare): verifierar
 * den faktiska fixen för {@link ForwardedHeadersIT}s dokumenterade
 * begränsning - att sessionscookien (JSESSIONID) förblev osäker trots
 * {@code X-Forwarded-Proto: https}, eftersom Tomcats egen
 * sessionscookie-generering ligger utanför ForwardedHeaderFilter-lagret.
 *
 * <p>Lösningen ({@code application-prod.yml},
 * {@code server.servlet.session.cookie.secure: true}) tvingar flaggan
 * ovillkorligt - alltså UTAN att behöva {@code X-Forwarded-Proto} alls,
 * till skillnad från remember-me-cookien. Det här testet aktiverar
 * profilen ({@code prod}) och skickar medvetet INGEN
 * {@code X-Forwarded-Proto}-header, för att just visa att den forcerade
 * flaggan inte är beroende av den headern.
 *
 * <p>Se {@link ForwardedHeadersIT} för motsvarande verifiering av att
 * default-profilen (lokal utveckling) medvetet INTE tvingar samma flagga.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("prod")
@TestPropertySource(properties = "winecellar.remember-me.key=forwarded-headers-prod-test-nyckel")
class ForwardedHeadersProdProfileIT extends SharedPostgres {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void skaSättaSäkerSessionscookieIProdprofilenÄvenUtanForwardedHttpsHeader() {
        ResponseEntity<String> svar = restTemplate.exchange("/login", HttpMethod.GET, null, String.class);

        assertThat(svar.getStatusCode()).isEqualTo(HttpStatus.OK);
        String sessionscookie = cookieMedPrefix(svar, "JSESSIONID=");
        assertThat(sessionscookie).isNotNull();
        assertThat(sessionscookie.toLowerCase()).contains("secure");
    }

    private static String cookieMedPrefix(ResponseEntity<String> svar, String prefix) {
        List<String> setCookieHeaders = svar.getHeaders().get(org.springframework.http.HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotNull();
        return setCookieHeaders.stream()
                .filter(cookie -> cookie.startsWith(prefix))
                .findFirst()
                .orElse(null);
    }
}
