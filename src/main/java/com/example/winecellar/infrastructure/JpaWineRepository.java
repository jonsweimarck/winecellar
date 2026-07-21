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

    @Override
    public List<Wine> search(String query) {
        return jpaRepository.search(query).stream()
                .map(JpaWineRepository::toDomain)
                .toList();
    }

    /** Används av acceptanstesterna för att nollställa tillstånd mellan scenarier. */
    public void deleteAll() {
        jpaRepository.deleteAll();
    }

    private static WineEntity toEntity(Wine wine) {
        WineEntity entity = new WineEntity();
        entity.setId(wine.id() != null ? wine.id().value() : null);
        entity.setName(wine.name());
        entity.setWineType(wine.wineType());
        entity.setProducer(wine.producer());
        entity.setCountry(wine.country());
        entity.setRegion(wine.region());
        entity.setSubregion(wine.subregion());
        entity.setGrapes(wine.grapes());
        entity.setVintage(wine.vintage());
        entity.setPurchaseDate(wine.purchaseDate());
        entity.setPrice(wine.price());
        entity.setQuantity(wine.quantity());
        entity.setPurchaseReason(wine.purchaseReason());
        entity.setTastingNotes(wine.tastingNotes());
        entity.setOwnRating(wine.ownRating());
        entity.setSystembolagetProductNumber(wine.systembolagetProductNumber());
        entity.setSystembolagetDescription(wine.systembolagetDescription());
        entity.setMunskankarnaReview(wine.munskankarnaReview());
        entity.setMunskankarnaRating(wine.munskankarnaRating());
        entity.setVivinoRating(wine.vivinoRating());
        entity.setOtherReference(wine.otherReference());
        entity.setLocation(wine.location());
        entity.setImage(wine.image());
        entity.setImageMimeType(wine.imageMimeType());
        return entity;
    }

    private static Wine toDomain(WineEntity entity) {
        return Wine.builder()
                .id(new WineId(entity.getId()))
                .name(entity.getName())
                .wineType(entity.getWineType())
                .producer(entity.getProducer())
                .country(entity.getCountry())
                .region(entity.getRegion())
                .subregion(entity.getSubregion())
                .grapes(entity.getGrapes())
                .vintage(entity.getVintage())
                .purchaseDate(entity.getPurchaseDate())
                .price(entity.getPrice())
                .quantity(entity.getQuantity())
                .purchaseReason(entity.getPurchaseReason())
                .tastingNotes(entity.getTastingNotes())
                .ownRating(entity.getOwnRating())
                .systembolagetProductNumber(entity.getSystembolagetProductNumber())
                .systembolagetDescription(entity.getSystembolagetDescription())
                .munskankarnaReview(entity.getMunskankarnaReview())
                .munskankarnaRating(entity.getMunskankarnaRating())
                .vivinoRating(entity.getVivinoRating())
                .otherReference(entity.getOtherReference())
                .location(entity.getLocation())
                .image(entity.getImage())
                .imageMimeType(entity.getImageMimeType())
                .build();
    }
}
