package com.example.winecellar.acceptance;

import com.example.winecellar.application.SortDirection;
import com.example.winecellar.application.SortField;
import com.example.winecellar.application.SearchCriteria;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sortering och filtrering delar samma stegklass, medvetet - inte en klass
 * per .feature-fil som resten av acceptanstesterna (se ListWinesSteps m.fl.).
 * Cucumber skapar en NY instans av varje stegklass per scenario, men om ett
 * scenario blandar steg från två olika klasser skapas BÅDA klasserna separat
 * - deras @Before-hooks körs var för sig, så ett WineService-fält i klass A
 * vore inte samma instans som klass B använder. Eftersom "att källaren
 * innehåller följande viner:" (Givet) måste dela WineService-instans med
 * både sorterings- och filtreringsstegen (När/Så) inom samma scenario,
 * måste alla tre ligga i en och samma klass. Se CLAUDE.md:s "Kända fällor".
 */
public class SearchAndFilterSteps {

    private static final Map<String, WineType> WINE_TYPE_BY_SWEDISH_LABEL = Map.of(
            "Rött", WineType.RED,
            "Vitt", WineType.WHITE,
            "Rosé", WineType.ROSE,
            "Mousserande", WineType.SPARKLING,
            "Starkvin", WineType.FORTIFIED
    );

    private WineService wineService;
    private List<Wine> result;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
    }

    @Givet("att källaren innehåller följande viner:")
    public void attKällarenInnehållerFöljandeViner(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            Wine.Builder wine = Wine.builder()
                    .name(row.get("namn"))
                    .wineType(wineTypeOrDefault(row))
                    .producer(stringOrDefault(row, "producent", "Okänd producent"))
                    .country(stringOrDefault(row, "land", "Okänt land"))
                    .region(blankToNull(row.get("region")))
                    .subregion(blankToNull(row.get("underregion")))
                    .grapes(blankToNull(row.get("druvor")))
                    .vintage(integerOrDefault(row, "årgång", 2020))
                    .quantity(1)
                    .location("Okänd plats")
                    .tastingNotes(blankToNull(row.get("tasting notes")))
                    .systembolagetDescription(blankToNull(row.get("systembolagets beskrivning")))
                    .munskankarnaReview(blankToNull(row.get("munskänkarnas bedömning")));
            String ratingLabel = row.get("eget betyg");
            if (ratingLabel != null && !ratingLabel.isBlank()) {
                wine.ownRating(Rating.fromLabel(ratingLabel));
            }
            wineService.save(wine.build());
        }
    }

    @När("jag sorterar vinlistan på {string} i stigande ordning")
    public void jagSorterarVinlistanPåStigande(String fieldLabel) {
        sort(fieldLabel, SortDirection.ASCENDING);
    }

    @När("jag sorterar vinlistan på {string} i fallande ordning")
    public void jagSorterarVinlistanPåFallande(String fieldLabel) {
        sort(fieldLabel, SortDirection.DESCENDING);
    }

    private void sort(String fieldLabel, SortDirection direction) {
        result = wineService.search(SearchCriteria.builder()
                .sortField(SortField.fromLabel(fieldLabel))
                .sortDirection(direction)
                .build());
    }

    @När("jag visar vinlistan utan filter")
    public void jagVisarVinlistanUtanFilter() {
        result = wineService.search(SearchCriteria.builder().build());
    }

    @När("jag filtrerar vinlistan på:")
    public void jagFiltrerarVinlistanPå(DataTable table) {
        Map<String, String> criteriaRow = table.asMap(String.class, String.class);
        SearchCriteria.Builder builder = SearchCriteria.builder();
        if (criteriaRow.containsKey("vintyp")) {
            builder.wineTypes(commaList(criteriaRow.get("vintyp")).stream()
                    .map(SearchAndFilterSteps::wineTypeFromSwedish)
                    .collect(Collectors.toSet()));
        }
        if (criteriaRow.containsKey("land")) {
            builder.countries(new HashSet<>(commaList(criteriaRow.get("land"))));
        }
        if (criteriaRow.containsKey("region")) {
            builder.regions(new HashSet<>(commaList(criteriaRow.get("region"))));
        }
        if (criteriaRow.containsKey("underregion")) {
            builder.subregions(new HashSet<>(commaList(criteriaRow.get("underregion"))));
        }
        result = wineService.search(builder.build());
    }

    @När("jag söker efter {string}")
    public void jagSökerEfter(String searchTerm) {
        result = wineService.search(SearchCriteria.builder().searchTerm(searchTerm).build());
    }

    @När("jag söker efter {string} och filtrerar vinlistan på:")
    public void jagSökerEfterOchFiltrerarPå(String searchTerm, DataTable table) {
        Map<String, String> criteriaRow = table.asMap(String.class, String.class);
        SearchCriteria.Builder builder = SearchCriteria.builder().searchTerm(searchTerm);
        if (criteriaRow.containsKey("vintyp")) {
            builder.wineTypes(commaList(criteriaRow.get("vintyp")).stream()
                    .map(SearchAndFilterSteps::wineTypeFromSwedish)
                    .collect(Collectors.toSet()));
        }
        result = wineService.search(builder.build());
    }

    @Så("visas vinerna i ordningen {string}")
    public void visasVinernaIOrdningen(String expectedOrder) {
        assertThat(result).extracting(Wine::name).containsExactlyElementsOf(commaList(expectedOrder));
    }

    @Så("ska vinlistan innehålla {string}")
    public void skaVinlistanInnehålla(String nameList) {
        assertThat(result).extracting(Wine::name).containsAll(commaList(nameList));
    }

    @Så("vinlistan ska inte innehålla {string}")
    public void vinlistanSkaInteInnehålla(String nameList) {
        assertThat(result).extracting(Wine::name).doesNotContain(commaList(nameList).toArray(new String[0]));
    }

    @Så("ska vinlistan vara tom")
    public void skaVinlistanVaraTom() {
        assertThat(result).isEmpty();
    }

    private static List<String> commaList(String value) {
        return List.of(value.split(",\\s*"));
    }

    private static WineType wineTypeOrDefault(Map<String, String> row) {
        String swedishLabel = row.get("vintyp");
        return swedishLabel == null || swedishLabel.isBlank() ? WineType.RED : wineTypeFromSwedish(swedishLabel);
    }

    private static WineType wineTypeFromSwedish(String swedishLabel) {
        WineType type = WINE_TYPE_BY_SWEDISH_LABEL.get(swedishLabel);
        if (type == null) {
            throw new IllegalArgumentException("Okänd vintyp i Gherkin-scenario: \"" + swedishLabel + "\"");
        }
        return type;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String stringOrDefault(Map<String, String> row, String column, String defaultValue) {
        String value = row.get(column);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int integerOrDefault(Map<String, String> row, String column, int defaultValue) {
        String value = row.get(column);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }
}
