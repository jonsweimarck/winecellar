package com.example.winecellar.domain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WineTest {

    private static final Wine BAROLO = Wine.builder()
            .name("Barolo").producer("Pio Cesare").vintage(2018)
            .build();

    @Test
    void skaMatchaVarandraNärAllaIdFältÄrLika() {
        Wine candidate = Wine.builder().name("Barolo").producer("Pio Cesare").vintage(2018).build();

        assertThat(candidate.matchesIdentityOf(BAROLO)).isTrue();
    }

    @Test
    void skaMatchaOavsettSkiftlägePåNamnOchProducent() {
        Wine candidate = Wine.builder().name("barolo").producer("PIO CESARE").vintage(2018).build();

        assertThat(candidate.matchesIdentityOf(BAROLO)).isTrue();
    }

    @Test
    void skaInteMatchaOmNamnetSkiljerSig() {
        Wine candidate = Wine.builder().name("Chablis").producer("Pio Cesare").vintage(2018).build();

        assertThat(candidate.matchesIdentityOf(BAROLO)).isFalse();
    }

    @Test
    void skaInteMatchaOmÅrgångenSkiljerSig() {
        Wine candidate = Wine.builder().name("Barolo").producer("Pio Cesare").vintage(2019).build();

        assertThat(candidate.matchesIdentityOf(BAROLO)).isFalse();
    }

    @Test
    void skaMatchaÄvenOmBaraNamnetÄrIfylltPåKandidaten() {
        Wine candidate = Wine.builder().name("Barolo").build();

        assertThat(candidate.matchesIdentityOf(BAROLO)).isTrue();
    }

    @Test
    void skaHaFullständigIdentitetBaraOmNamnProducentOchÅrgångÄrIfyllda() {
        assertThat(BAROLO.hasCompleteIdentity()).isTrue();
        assertThat(Wine.builder().name("Barolo").build().hasCompleteIdentity()).isFalse();
        assertThat(Wine.builder().name("Barolo").producer("Pio Cesare").build().hasCompleteIdentity()).isFalse();
    }

    /**
     * WINE-51: `tags` är ALDRIG `null` (till skillnad från de flesta
     * andra fälten) - Builder.tags(null) normaliserar till en tom mängd,
     * så anropande kod aldrig behöver null-kolla `wine.tags()`.
     */
    @Test
    void skaHaEnTomTaggmängdSomDefault() {
        assertThat(BAROLO.tags()).isEmpty();
    }

    @Test
    void skaNormaliseraNullTaggarTillEnTomMängd() {
        Wine wine = Wine.builder().name("Barolo").tags(null).build();

        assertThat(wine.tags()).isEmpty();
    }

    /**
     * Taggar renderas i alfabetisk ordning överallt (vinlistans kort,
     * vinformulärets chips, filterpanelen) utan att varje anropsplats
     * behöver sortera själv - Builder.tags(...) lagrar alltid en TreeSet,
     * oavsett vilken Set-implementation som skickades in.
     */
    @Test
    void skaSorteraTaggarAlfabetiskt() {
        Set<String> insättningsordning = new LinkedHashSet<>(List.of("Vardag", "Favorit", "Festvin"));

        Wine wine = Wine.builder().name("Barolo").tags(insättningsordning).build();

        assertThat(wine.tags()).containsExactly("Favorit", "Festvin", "Vardag");
    }
}
