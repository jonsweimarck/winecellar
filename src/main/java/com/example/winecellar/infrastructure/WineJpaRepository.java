package com.example.winecellar.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface WineJpaRepository extends JpaRepository<WineEntity, Long> {

    List<WineEntity> findByOwnerId(Long ownerId);

    Optional<WineEntity> findByIdAndOwnerId(Long id, Long ownerId);

    /**
     * search_vector är inte ett mappat fält på WineEntity (underhålls av
     * en trigger sedan 2026-07-25, se schema.sql) - därför en native
     * query med en explicit kolumnlista istället för "SELECT *"
     * (Hibernate kan annars inte mappa den extra, omappade kolumnen till
     * entiteten). plainto_tsquery('swedish_unaccent', ...) tolkar
     * sökordet med samma konfiguration som kolumnen genererades med
     * (böjningsform-medveten OCH okänslig för diakritiska tecken, se
     * WINE-7), ts_rank sorterar bästa träff först.
     *
     * Kolumnlistan måste hållas i synk med varje mappat fält på
     * WineEntity, inte bara de ursprungliga - owner_id (WINE-10) saknades
     * här från början (upptäcktes i produktion som "The column name
     * owner_id was not found in this ResultSet", eftersom Hibernate
     * försöker hydrera owner-relationen från samma radresultat). Samma
     * fälla återkommer för varje framtida nytt mappat fält på WineEntity.
     *
     * `:ownerId IS NULL OR owner_id = :ownerId` (WINE-13) - ett null
     * ownerId betyder oscopeat, inte "matcha bara ägarlösa viner". Se
     * WineRepository för null-konventionens (numera historiska)
     * bakgrund i de borttagna admin/readonly-kontona.
     */
    @Query(value = """
            SELECT id, name, wine_type, producer, country, region, subregion, grapes, vintage,
                   purchase_date, price, quantity, purchase_reason, tasting_notes, own_rating,
                   systembolaget_product_number, systembolaget_description, munskankarna_review,
                   munskankarna_rating, vivino_rating, other_reference, location, image, image_mime_type,
                   owner_id
            FROM wines
            WHERE search_vector @@ plainto_tsquery('swedish_unaccent', :query)
              AND (:ownerId IS NULL OR owner_id = :ownerId)
            ORDER BY ts_rank(search_vector, plainto_tsquery('swedish_unaccent', :query)) DESC
            """, nativeQuery = true)
    List<WineEntity> searchByOwner(@Param("query") String query, @Param("ownerId") Long ownerId);
}
