package com.example.winecellar.application;

import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;

import java.util.List;
import java.util.Optional;

/**
 * WINE-13: alla läsmetoder tar ett `owner`-argument, nullable - `null`
 * betyder "oscopeat" (returnera/matcha oavsett ägare), inte "matcha bara
 * viner utan ägare". Ursprungligen till för de hårdkodade admin/readonly-
 * kontona (som saknade UserId och medvetet var oscopeade under
 * övergången till WINE-15, se WineController.currentOwner(...)) - de är
 * borttagna sedan WINE-15, men själva null-konventionen behölls (kostar
 * inget, och en framtida "adminvy över alla användare" skulle kunna
 * återanvända den).
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
     * Fritextsökning över namn, producent, tasting notes, Systembolagets
     * beskrivning och Munskänkarnas bedömning. Implementationerna behöver
     * INTE bete sig identiskt - JpaWineRepository använder Postgres
     * tsvector (böjningsform-medveten, rankad), InMemoryWineRepository en
     * enklare skiftlägesokänslig delsträngsmatchning för tester som inte
     * bryr sig om just den kvaliteten. Se CLAUDE.md.
     */
    List<Wine> searchByOwner(String query, UserId owner);
}
