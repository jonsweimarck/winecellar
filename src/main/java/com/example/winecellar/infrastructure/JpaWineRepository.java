package com.example.winecellar.infrastructure;

import com.example.winecellar.application.WineRepository;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaWineRepository implements WineRepository {

    private final WineJpaRepository jpaRepository;

    public JpaWineRepository(WineJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Wine save(Wine wine) {
        return toDomain(jpaRepository.save(toEntity(wine)));
    }

    @Override
    public List<Wine> findAll() {
        return jpaRepository.findAll().stream()
                .map(JpaWineRepository::toDomain)
                .toList();
    }

    @Override
    public Optional<Wine> findById(WineId id) {
        return jpaRepository.findById(id.value()).map(JpaWineRepository::toDomain);
    }

    @Override
    public void deleteById(WineId id) {
        jpaRepository.deleteById(id.value());
    }

    /** Används av acceptanstesterna för att nollställa tillstånd mellan scenarier. */
    public void deleteAll() {
        jpaRepository.deleteAll();
    }

    private static WineEntity toEntity(Wine wine) {
        return new WineEntity(
                wine.id() != null ? wine.id().value() : null,
                wine.name(),
                wine.wineType(),
                wine.producer(),
                wine.country(),
                wine.vintage(),
                wine.quantity(),
                wine.location());
    }

    private static Wine toDomain(WineEntity entity) {
        return new Wine(
                new WineId(entity.getId()),
                entity.getName(),
                entity.getWineType(),
                entity.getProducer(),
                entity.getCountry(),
                entity.getVintage(),
                entity.getQuantity(),
                entity.getLocation());
    }
}
