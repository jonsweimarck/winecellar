package com.example.winecellar.application;

import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;

import java.util.List;
import java.util.Optional;

public interface WineRepository {

    Wine save(Wine wine);

    List<Wine> findAll();

    Optional<Wine> findById(WineId id);

    void deleteById(WineId id);

    /**
     * Fritextsökning över namn, producent, tasting notes, Systembolagets
     * beskrivning och Munskänkarnas bedömning. Implementationerna behöver
     * INTE bete sig identiskt - JpaWineRepository använder Postgres
     * tsvector (böjningsform-medveten, rankad), InMemoryWineRepository en
     * enklare skiftlägesokänslig delsträngsmatchning för tester som inte
     * bryr sig om just den kvaliteten. Se CLAUDE.md.
     */
    List<Wine> search(String query);
}
