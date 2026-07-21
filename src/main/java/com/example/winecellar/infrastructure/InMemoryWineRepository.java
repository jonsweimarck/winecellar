package com.example.winecellar.infrastructure;

import com.example.winecellar.application.WineRepository;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Används numera enbart av CRUD-acceptanstesterna (lagga-till-vin.feature m.fl.)
 * - inte Spring-hanterad. Produktionskonfigurationen använder
 * {@link JpaWineRepository} mot Postgres, se vin-persistens.feature.
 */
public class InMemoryWineRepository implements WineRepository {

    private final Map<Long, Wine> wines = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Wine save(Wine wine) {
        Wine toStore = wine.id() != null
                ? wine
                : wine.toBuilder().id(new WineId(nextId.getAndIncrement())).build();
        wines.put(toStore.id().value(), toStore);
        return toStore;
    }

    @Override
    public List<Wine> findAll() {
        return List.copyOf(wines.values());
    }

    @Override
    public Optional<Wine> findById(WineId id) {
        return Optional.ofNullable(wines.get(id.value()));
    }

    @Override
    public void deleteById(WineId id) {
        wines.remove(id.value());
    }

    /**
     * Enkel skiftlägesokänslig delsträngsmatchning - ingen böjningsform-
     * medvetenhet eller rankning som JpaWineRepositorys riktiga
     * tsvector-sökning. Fullt tillräckligt för de acceptanstester som
     * bara bryr sig om VILKA viner som matchar, inte i vilken ordning.
     */
    @Override
    public List<Wine> search(String query) {
        String normaliseradSökterm = query.toLowerCase(Locale.ROOT);
        return wines.values().stream()
                .filter(vin -> matchar(vin, normaliseradSökterm))
                .toList();
    }

    private static boolean matchar(Wine vin, String normaliseradSökterm) {
        return innehåller(vin.name(), normaliseradSökterm)
                || innehåller(vin.producer(), normaliseradSökterm)
                || innehåller(vin.tastingNotes(), normaliseradSökterm)
                || innehåller(vin.systembolagetDescription(), normaliseradSökterm)
                || innehåller(vin.munskankarnaReview(), normaliseradSökterm);
    }

    private static boolean innehåller(String fältvärde, String normaliseradSökterm) {
        return fältvärde != null && fältvärde.toLowerCase(Locale.ROOT).contains(normaliseradSökterm);
    }
}
