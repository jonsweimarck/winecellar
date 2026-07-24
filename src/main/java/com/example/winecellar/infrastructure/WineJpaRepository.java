package com.example.winecellar.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface WineJpaRepository extends JpaRepository<WineEntity, Long> {

    /**
     * search_vector är en genererad kolumn (se schema.sql), inte ett
     * mappat fält på WineEntity - därför en native query med en explicit
     * kolumnlista istället för "SELECT *" (Hibernate kan annars inte
     * mappa den extra, omappade kolumnen till entiteten).
     * plainto_tsquery('swedish_unaccent', ...) tolkar sökordet med samma
     * konfiguration som kolumnen genererades med (böjningsform-medveten
     * OCH okänslig för diakritiska tecken, se schema.sql/WINE-7),
     * ts_rank sorterar bästa träff först.
     *
     * Kolumnlistan måste hållas i synk med varje mappat fält på
     * WineEntity, inte bara de ursprungliga - owner_id (WINE-10) saknades
     * här från början (upptäcktes i produktion som "The column name
     * owner_id was not found in this ResultSet", eftersom Hibernate
     * försöker hydrera owner-relationen från samma radresultat). Samma
     * fälla återkommer för varje framtida nytt mappat fält på WineEntity.
     */
    @Query(value = """
            SELECT id, name, wine_type, producer, country, region, subregion, grapes, vintage,
                   purchase_date, price, quantity, purchase_reason, tasting_notes, own_rating,
                   systembolaget_product_number, systembolaget_description, munskankarna_review,
                   munskankarna_rating, vivino_rating, other_reference, location, image, image_mime_type,
                   owner_id
            FROM wines
            WHERE search_vector @@ plainto_tsquery('swedish_unaccent', :query)
            ORDER BY ts_rank(search_vector, plainto_tsquery('swedish_unaccent', :query)) DESC
            """, nativeQuery = true)
    List<WineEntity> search(@Param("query") String query);
}
