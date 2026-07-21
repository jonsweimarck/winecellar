package com.example.winecellar.acceptance;

import com.example.winecellar.application.SorteringsRiktning;
import com.example.winecellar.application.Sorteringsfält;
import com.example.winecellar.application.Sökkriterier;
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
 * per .feature-fil som resten av acceptanstesterna (se ListaVinerSteps m.fl.).
 * Cucumber skapar en NY instans av varje stegklass per scenario, men om ett
 * scenario blandar steg från två olika klasser skapas BÅDA klasserna separat
 * - deras @Before-hooks körs var för sig, så ett WineService-fält i klass A
 * vore inte samma instans som klass B använder. Eftersom "att källaren
 * innehåller följande viner:" (Givet) måste dela WineService-instans med
 * både sorterings- och filtreringsstegen (När/Så) inom samma scenario,
 * måste alla tre ligga i en och samma klass. Se CLAUDE.md:s "Kända fällor".
 */
public class SökOchFilterSteps {

    private static final Map<String, WineType> VINTYP_PER_SVENSKA = Map.of(
            "Rött", WineType.RED,
            "Vitt", WineType.WHITE,
            "Rosé", WineType.ROSE,
            "Mousserande", WineType.SPARKLING,
            "Starkvin", WineType.FORTIFIED
    );

    private WineService wineService;
    private List<Wine> resultat;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
    }

    @Givet("att källaren innehåller följande viner:")
    public void attKällarenInnehållerFöljandeViner(DataTable tabell) {
        for (Map<String, String> rad : tabell.asMaps()) {
            Wine.Builder vin = Wine.builder()
                    .name(rad.get("namn"))
                    .wineType(vintypEllerStandard(rad))
                    .producer(strängEllerStandard(rad, "producent", "Okänd producent"))
                    .country(strängEllerStandard(rad, "land", "Okänt land"))
                    .region(tomBlirNull(rad.get("region")))
                    .subregion(tomBlirNull(rad.get("underregion")))
                    .vintage(heltalEllerStandard(rad, "årgång", 2020))
                    .quantity(1)
                    .location("Okänd plats")
                    .tastingNotes(tomBlirNull(rad.get("tasting notes")))
                    .systembolagetDescription(tomBlirNull(rad.get("systembolagets beskrivning")))
                    .munskankarnaReview(tomBlirNull(rad.get("munskänkarnas bedömning")));
            String betygEtikett = rad.get("eget betyg");
            if (betygEtikett != null && !betygEtikett.isBlank()) {
                vin.ownRating(Rating.fraEtikett(betygEtikett));
            }
            wineService.save(vin.build());
        }
    }

    @När("jag sorterar vinlistan på {string} i stigande ordning")
    public void jagSorterarVinlistanPåStigande(String fältEtikett) {
        sortera(fältEtikett, SorteringsRiktning.STIGANDE);
    }

    @När("jag sorterar vinlistan på {string} i fallande ordning")
    public void jagSorterarVinlistanPåFallande(String fältEtikett) {
        sortera(fältEtikett, SorteringsRiktning.FALLANDE);
    }

    private void sortera(String fältEtikett, SorteringsRiktning riktning) {
        resultat = wineService.sök(Sökkriterier.builder()
                .sortering(Sorteringsfält.frånEtikett(fältEtikett))
                .riktning(riktning)
                .build());
    }

    @När("jag visar vinlistan utan filter")
    public void jagVisarVinlistanUtanFilter() {
        resultat = wineService.sök(Sökkriterier.builder().build());
    }

    @När("jag filtrerar vinlistan på:")
    public void jagFiltrerarVinlistanPå(DataTable tabell) {
        Map<String, String> kriterierRad = tabell.asMap(String.class, String.class);
        Sökkriterier.Builder builder = Sökkriterier.builder();
        if (kriterierRad.containsKey("vintyp")) {
            builder.vintyper(kommalista(kriterierRad.get("vintyp")).stream()
                    .map(SökOchFilterSteps::vintypFrånSvenska)
                    .collect(Collectors.toSet()));
        }
        if (kriterierRad.containsKey("land")) {
            builder.länder(new HashSet<>(kommalista(kriterierRad.get("land"))));
        }
        if (kriterierRad.containsKey("region")) {
            builder.regioner(new HashSet<>(kommalista(kriterierRad.get("region"))));
        }
        if (kriterierRad.containsKey("underregion")) {
            builder.underregioner(new HashSet<>(kommalista(kriterierRad.get("underregion"))));
        }
        resultat = wineService.sök(builder.build());
    }

    @När("jag söker efter {string}")
    public void jagSökerEfter(String sökterm) {
        resultat = wineService.sök(Sökkriterier.builder().sökterm(sökterm).build());
    }

    @När("jag söker efter {string} och filtrerar vinlistan på:")
    public void jagSökerEfterOchFiltrerarPå(String sökterm, DataTable tabell) {
        Map<String, String> kriterierRad = tabell.asMap(String.class, String.class);
        Sökkriterier.Builder builder = Sökkriterier.builder().sökterm(sökterm);
        if (kriterierRad.containsKey("vintyp")) {
            builder.vintyper(kommalista(kriterierRad.get("vintyp")).stream()
                    .map(SökOchFilterSteps::vintypFrånSvenska)
                    .collect(Collectors.toSet()));
        }
        resultat = wineService.sök(builder.build());
    }

    @Så("visas vinerna i ordningen {string}")
    public void visasVinernaIOrdningen(String förväntadOrdning) {
        assertThat(resultat).extracting(Wine::name).containsExactlyElementsOf(kommalista(förväntadOrdning));
    }

    @Så("ska vinlistan innehålla {string}")
    public void skaVinlistanInnehålla(String namnLista) {
        assertThat(resultat).extracting(Wine::name).containsAll(kommalista(namnLista));
    }

    @Så("vinlistan ska inte innehålla {string}")
    public void vinlistanSkaInteInnehålla(String namnLista) {
        assertThat(resultat).extracting(Wine::name).doesNotContain(kommalista(namnLista).toArray(new String[0]));
    }

    @Så("ska vinlistan vara tom")
    public void skaVinlistanVaraTom() {
        assertThat(resultat).isEmpty();
    }

    private static List<String> kommalista(String värde) {
        return List.of(värde.split(",\\s*"));
    }

    private static WineType vintypEllerStandard(Map<String, String> rad) {
        String svenska = rad.get("vintyp");
        return svenska == null || svenska.isBlank() ? WineType.RED : vintypFrånSvenska(svenska);
    }

    private static WineType vintypFrånSvenska(String svenska) {
        WineType typ = VINTYP_PER_SVENSKA.get(svenska);
        if (typ == null) {
            throw new IllegalArgumentException("Okänd vintyp i Gherkin-scenario: \"" + svenska + "\"");
        }
        return typ;
    }

    private static String tomBlirNull(String värde) {
        return värde == null || värde.isBlank() ? null : värde;
    }

    private static String strängEllerStandard(Map<String, String> rad, String kolumn, String standardvärde) {
        String värde = rad.get(kolumn);
        return värde == null || värde.isBlank() ? standardvärde : värde;
    }

    private static int heltalEllerStandard(Map<String, String> rad, String kolumn, int standardvärde) {
        String värde = rad.get(kolumn);
        return värde == null || värde.isBlank() ? standardvärde : Integer.parseInt(värde);
    }
}
