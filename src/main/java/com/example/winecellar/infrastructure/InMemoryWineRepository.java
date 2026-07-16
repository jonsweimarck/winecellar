package com.example.winecellar.infrastructure;

import com.example.winecellar.application.WineRepository;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;

import java.util.List;
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
                : new Wine(new WineId(nextId.getAndIncrement()), wine.name(), wine.wineType(), wine.producer(),
                        wine.country(), wine.vintage(), wine.quantity(), wine.location(), wine.image(), wine.imageMimeType());
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
}
