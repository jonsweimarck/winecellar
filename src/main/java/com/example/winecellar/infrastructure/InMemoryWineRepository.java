package com.example.winecellar.infrastructure;

import com.example.winecellar.application.WineRepository;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

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
     * Diakritiska tecken (t.ex. "ñ") normaliseras bort på samma sätt som
     * JpaWineRepositorys swedish_unaccent-textsökkonfiguration (WINE-7,
     * se schema.sql) - annars hade "albarino" inte matchat druvan
     * "Albariño" här, till skillnad från i produktion.
     */
    @Override
    public List<Wine> search(String query) {
        String normalizedSearchTerm = stripDiacritics(query.toLowerCase(Locale.ROOT));
        return wines.values().stream()
                .filter(wine -> matches(wine, normalizedSearchTerm))
                .toList();
    }

    private static boolean matches(Wine wine, String normalizedSearchTerm) {
        return contains(wine.name(), normalizedSearchTerm)
                || contains(wine.producer(), normalizedSearchTerm)
                || contains(wine.grapes(), normalizedSearchTerm)
                || contains(wine.tastingNotes(), normalizedSearchTerm)
                || contains(wine.systembolagetDescription(), normalizedSearchTerm)
                || contains(wine.munskankarnaReview(), normalizedSearchTerm);
    }

    private static boolean contains(String fieldValue, String normalizedSearchTerm) {
        return fieldValue != null
                && stripDiacritics(fieldValue.toLowerCase(Locale.ROOT)).contains(normalizedSearchTerm);
    }

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");

    private static String stripDiacritics(String text) {
        return COMBINING_MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
    }
}
