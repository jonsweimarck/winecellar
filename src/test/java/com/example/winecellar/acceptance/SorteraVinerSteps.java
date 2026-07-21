package com.example.winecellar.acceptance;

import com.example.winecellar.application.SorteringsRiktning;
import com.example.winecellar.application.Sorteringsfält;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SorteraVinerSteps {

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
                    .wineType(WineType.RED)
                    .producer("Okänd producent")
                    .country("Okänt land")
                    .vintage(heltalEllerStandard(rad, "årgång", 2020))
                    .quantity(1)
                    .location("Okänd plats");
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
        resultat = wineService.sök(Sorteringsfält.frånEtikett(fältEtikett), riktning);
    }

    @Så("visas vinerna i ordningen {string}")
    public void visasVinernaIOrdningen(String förväntadOrdning) {
        List<String> förväntadeNamn = List.of(förväntadOrdning.split(",\\s*"));
        assertThat(resultat).extracting(Wine::name).containsExactlyElementsOf(förväntadeNamn);
    }

    private static int heltalEllerStandard(Map<String, String> rad, String kolumn, int standardvärde) {
        String värde = rad.get(kolumn);
        return värde == null || värde.isBlank() ? standardvärde : Integer.parseInt(värde);
    }
}
