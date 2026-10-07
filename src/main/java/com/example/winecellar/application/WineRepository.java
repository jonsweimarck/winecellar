package com.example.winecellar.application;

import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;

import java.util.List;
import java.util.Optional;

/**
 * Alla metoder som tar ett `owner`-argument är FAIL-CLOSED (WINE-64):
 * `null` kastar NullPointerException i stället för att betyda "alla
 * användares data" (den gamla WINE-13-konventionen, borttagen som en
 * latent dataläckagefälla).
 */
public interface WineRepository {

    Wine save(Wine wine);

    List<Wine> findAllByOwner(UserId owner);

    Optional<Wine> findByIdAndOwner(WineId id, UserId owner);

    /**
     * Scopas INTE här - anropande kod (WineService) ansvarar för att
     * redan ha verifierat ägarskap via findByIdAndOwner innan borttagning,
     * samma "reposotoryn är dum CRUD, applikationslagret orkestrerar"-
     * princip som redan gäller (se ADR 0006).
     */
    void deleteById(WineId id);

    /**
     * WINE-61 (radering av en användare): tar bort ALLA ägarens viner, inklusive
     * taggar. {@code owner} får inte vara null (som för alla owner-metoder).
     */
    void deleteAllByOwner(UserId owner);

    /**
     * Fritextsökning över namn, producent, tasting notes, Systembolagets
     * beskrivning och Munskänkarnas bedömning. Implementationerna behöver
     * INTE bete sig identiskt - JpaWineRepository använder Postgres
     * tsvector (böjningsform-medveten, rankad), InMemoryWineRepository en
     * enklare skiftlägesokänslig delsträngsmatchning för tester som inte
     * bryr sig om just den kvaliteten. Se CLAUDE.md.
     */
    List<Wine> searchByOwner(String query, UserId owner);
}
