package com.example.winecellar.domain;

import org.junit.jupiter.api.Test;

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
}
