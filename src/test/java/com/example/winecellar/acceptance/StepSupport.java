package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;

/**
 * Delad mellan flera stegklasser för att slippa identisk uppslags- och
 * testdatakod i var och en (tidigare dupplicerad, med sinsemellan olika
 * platshållarvärden, i AddWineSteps/EditWineSteps/RemoveWineSteps/
 * PersistenceSteps).
 */
final class StepSupport {

    private StepSupport() {
    }

    static Wine wineWithName(String name) {
        return wineWithNameAndQuantity(name, 1);
    }

    static Wine wineWithNameAndQuantity(String name, int quantity) {
        return Wine.builder()
                .name(name).wineType(WineType.RED).producer("Okänd producent").country("Okänt land")
                .vintage(2020).quantity(quantity).location("Okänd plats")
                .build();
    }

    static Wine findWine(WineService wineService, String name) {
        return wineService.listWines().stream()
                .filter(wine -> wine.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Inget vin med namnet " + name + " hittades"));
    }
}
