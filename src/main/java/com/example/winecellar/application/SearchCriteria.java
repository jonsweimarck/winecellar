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
 * `searchTerm` (null/blankt = ingen sökning) avgör BASLISTAN (findAll()
 * eller ett träffresultat från WineRepository.search(...)) - facetterna
 * och sorteringen appliceras sedan ovanpå den, i den ordningen, se
 * WineService.search(...).
 */
public record SearchCriteria(
        String searchTerm,
        Set<WineType> wineTypes,
        Set<String> countries,
        Set<String> regions,
        Set<String> subregions,
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

        public Builder sortField(SortField sortField) {
            this.sortField = sortField;
            return this;
        }

        public Builder sortDirection(SortDirection sortDirection) {
            this.sortDirection = sortDirection;
            return this;
        }

        public SearchCriteria build() {
            return new SearchCriteria(searchTerm, wineTypes, countries, regions, subregions, sortField, sortDirection);
        }
    }
}
