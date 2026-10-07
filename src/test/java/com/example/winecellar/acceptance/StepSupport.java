package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;

/**
 * Delad mellan flera stegklasser för att slippa identisk uppslags- och
 * testdatakod i var och en (tidigare dupplicerad, med sinsemellan olika
 * platshållarvärden, i AddWineSteps/EditWineSteps/RemoveWineSteps/
 * PersistenceSteps).
 */
final class StepSupport {

    /** WINE-64: repositoryna kastar vid null-ägare, så även de rena in-memory-scenarierna behöver en riktig ägare. */
    static final UserId OWNER = new UserId(1L);

    private StepSupport() {
    }

    static Wine wineWithName(String name) {
        return wineWithNameAndQuantity(name, 1);
    }

    static Wine wineWithNameAndQuantity(String name, int quantity) {
        return Wine.builder()
                .name(name).wineType(WineType.RED).producer("Okänd producent").country("Okänt land")
                .vintage(2020).quantity(quantity).location("Okänd plats").owner(OWNER)
                .build();
    }

    static Wine findWine(WineService wineService, String name) {
        return findWine(wineService, OWNER, name);
    }

    static Wine findWine(WineService wineService, UserId owner, String name) {
        return wineService.listWines(owner).stream()
                .filter(wine -> wine.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Inget vin med namnet " + name + " hittades"));
    }
}
