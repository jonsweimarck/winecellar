package com.example.winecellar.application;

import com.example.winecellar.domain.WineType;

import java.util.Set;
import java.util.TreeSet;

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
 * `searchTerm` (null/blankt = ingen sökning) avgör BASLISTAN (findAll()
 * eller ett träffresultat från WineRepository.search(...)) - facetterna
 * och sorteringen appliceras sedan ovanpå den, i den ordningen, se
 * WineService.search(...).
 *
 * `minQuantity` (WINE-41, semantiken ändrad från "fler än" till "minst"
 * WINE-42 efter att -1 visade sig vara ett obekvämt sätt att se
 * utdruckna viner) är ett tröskelvärde, inte en facett - ett vin matchar
 * om dess antal flaskor är STÖRRE ÄN ELLER LIKA MED värdet, så 0 visar
 * även utdruckna viner. Byggarens egen default (0) betyder alltså "ingen
 * begränsning" - samma neutrala värde som en tom facett. Kombineras med
 * övriga facetter via OCH, precis som de andra fälten i den här
 * recorden.
 */
public record SearchCriteria(
        String searchTerm,
        Set<WineType> wineTypes,
        Set<String> countries,
        Set<String> regions,
        Set<String> subregions,
        Set<String> tags,
        int minQuantity,
        SortField sortField,
        SortDirection sortDirection
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String searchTerm;
        private Set<WineType> wineTypes = Set.of();
        private Set<String> countries = Set.of();
        private Set<String> regions = Set.of();
        private Set<String> subregions = Set.of();
        private Set<String> tags = Set.of();
        private int minQuantity = 0;
        private SortField sortField = SortField.NAME;
        private SortDirection sortDirection = SortDirection.ASCENDING;

        public Builder searchTerm(String searchTerm) {
            this.searchTerm = searchTerm;
            return this;
        }

        public Builder wineTypes(Set<WineType> wineTypes) {
            this.wineTypes = wineTypes;
            return this;
        }

        public Builder countries(Set<String> countries) {
            this.countries = countries;
            return this;
        }

        public Builder regions(Set<String> regions) {
            this.regions = regions;
            return this;
        }

        public Builder subregions(Set<String> subregions) {
            this.subregions = subregions;
            return this;
        }

        /**
         * WINE-51: samma OCH-mellan-facetter/ELLER-inom-facetten-princip
         * som övriga facetter (se klassens Javadoc) - ett vin matchar om
         * det har MINST EN av de valda taggarna. Normaliseras till samma
         * skiftlägesokänsliga `TreeSet` (`String.CASE_INSENSITIVE_ORDER`)
         * som `Wine.tags()` redan använder - annars blir
         * `WineService.search(...)`s `criteria.tags()::contains`-jämförelse
         * mot `Wine.tags()` skiftlägeskänslig, trots att `Wine.tags()`
         * själv inte är det (granskningsfynd, WINE-51).
         */
        public Builder tags(Set<String> tags) {
            this.tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            if (tags != null) {
                this.tags.addAll(tags);
            }
            return this;
        }

        public Builder minQuantity(int minQuantity) {
            this.minQuantity = minQuantity;
            return this;
        }

        public Builder sortField(SortField sortField) {
            this.sortField = sortField;
            return this;
        }

        public Builder sortDirection(SortDirection sortDirection) {
            this.sortDirection = sortDirection;
            return this;
        }

        public SearchCriteria build() {
            return new SearchCriteria(searchTerm, wineTypes, countries, regions, subregions, tags, minQuantity, sortField, sortDirection);
        }
    }
}
