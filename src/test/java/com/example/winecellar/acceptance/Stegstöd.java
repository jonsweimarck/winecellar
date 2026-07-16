package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;

/**
 * Delad mellan flera stegklasser för att slippa identisk uppslags- och
 * testdatakod i var och en (tidigare dupplicerad, med sinsemellan olika
 * platshållarvärden, i LaggTillVinSteps/RedigeraVinSteps/TaBortVinSteps/
 * PersistenceSteps).
 */
final class Stegstöd {

    private Stegstöd() {
    }

    static Wine vinMedNamn(String namn) {
        return vinMedNamnOchAntal(namn, 1);
    }

    static Wine vinMedNamnOchAntal(String namn, int antal) {
        return new Wine(null, namn, WineType.RED, "Okänd producent", "Okänt land", 2020, antal, "Okänd plats", null, null);
    }

    static Wine hittaVin(WineService wineService, String namn) {
        return wineService.listWines().stream()
                .filter(vin -> vin.name().equals(namn))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Inget vin med namnet " + namn + " hittades"));
    }
}
