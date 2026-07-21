package com.example.winecellar.application;

import com.example.winecellar.domain.WineType;

import java.util.Set;

/**
 * Facetterna är oberoende av varandra och kombineras med OCH (ett vin
 * måste matcha samtliga icke-tomma facetter) - inom en facett är det OCH
 * ett tomt set som betyder "ingen begränsning", varje enskilt värde i
 * ett icke-tomt set är ELLER (t.ex. vintyper = {RED, WHITE} matchar rött
 * ELLER vitt). Land/region/underregion är oberoende fält på Wine, inte en
 * riktig databashierarki - att kryssa i en underregion utan att kryssa i
 * dess land fungerar än ändå korrekt, eftersom underregionsvärdet i
 * praktiken bara förekommer på viner från just det landet.
 *
 * Sex fält nu (fyra filterfacetter + sortering), växer troligen med ett
 * sökterm-fält när fritextsökningen byggs - därför Builder redan nu
 * (samma resonemang som Wine.Builder), inte en positionell konstruktor
 * med 1-3 satta fält på de flesta anropsplatser.
 */
public record Sökkriterier(
        Set<WineType> vintyper,
        Set<String> länder,
        Set<String> regioner,
        Set<String> underregioner,
        Sorteringsfält sortering,
        SorteringsRiktning riktning
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Set<WineType> vintyper = Set.of();
        private Set<String> länder = Set.of();
        private Set<String> regioner = Set.of();
        private Set<String> underregioner = Set.of();
        private Sorteringsfält sortering = Sorteringsfält.NAMN;
        private SorteringsRiktning riktning = SorteringsRiktning.STIGANDE;

        public Builder vintyper(Set<WineType> vintyper) {
            this.vintyper = vintyper;
            return this;
        }

        public Builder länder(Set<String> länder) {
            this.länder = länder;
            return this;
        }

        public Builder regioner(Set<String> regioner) {
            this.regioner = regioner;
            return this;
        }

        public Builder underregioner(Set<String> underregioner) {
            this.underregioner = underregioner;
            return this;
        }

        public Builder sortering(Sorteringsfält sortering) {
            this.sortering = sortering;
            return this;
        }

        public Builder riktning(SorteringsRiktning riktning) {
            this.riktning = riktning;
            return this;
        }

        public Sökkriterier build() {
            return new Sökkriterier(vintyper, länder, regioner, underregioner, sortering, riktning);
        }
    }
}
