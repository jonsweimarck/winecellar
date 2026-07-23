package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.Och;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AddWineSteps {

    private static final Map<String, WineType> WINE_TYPES = Map.of(
            "rött", WineType.RED,
            "vitt", WineType.WHITE,
            "rosé", WineType.ROSE,
            "mousserande", WineType.SPARKLING,
            "starkvin", WineType.FORTIFIED
    );

    private WineService wineService;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
    }

    @Givet("att källaren är tom")
    public void attKällarenÄrTom() {
        assertThat(wineService.listWines()).isEmpty();
    }

    @När("jag lägger till ett vin med följande uppgifter:")
    public void jagLäggerTillEttVinMedFöljandeUppgifter(DataTable dataTable) {
        Map<String, String> data = dataTable.asMap(String.class, String.class);
        Wine newWine = Wine.builder()
                .name(data.get("namn"))
                .wineType(wineTypeFromSwedish(data.get("typ")))
                .producer(data.get("producent"))
                .country(data.get("land"))
                .vintage(Integer.parseInt(data.get("årgång")))
                .quantity(Integer.parseInt(data.get("flaskor")))
                .location(data.get("plats"))
                .build();
        wineService.save(newWine);
    }

    @När("jag lägger till ett vin med bara namnet {string}")
    public void jagLäggerTillEttVinMedBaraNamnet(String name) {
        wineService.save(Wine.builder().name(name).build());
    }

    @Så("ska källaren innehålla {int} vin")
    public void skaKällarenInnehålla(int count) {
        assertThat(wineService.listWines()).hasSize(count);
    }

    @Och("vinet {string} ska visas med {int} flaskor i {string}")
    public void vinetSkaVisasMedFlaskorI(String name, int bottles, String location) {
        Wine wine = StepSupport.findWine(wineService, name);
        assertThat(wine.quantity()).isEqualTo(bottles);
        assertThat(wine.location()).isEqualTo(location);
    }

    @Och("vinet {string} ska sakna övriga uppgifter")
    public void vinetSkaSaknaÖvrigaUppgifter(String name) {
        Wine wine = StepSupport.findWine(wineService, name);
        assertThat(wine.wineType()).isNull();
        assertThat(wine.producer()).isNull();
        assertThat(wine.country()).isNull();
        assertThat(wine.vintage()).isNull();
        assertThat(wine.quantity()).isNull();
        assertThat(wine.location()).isNull();
    }

    private WineType wineTypeFromSwedish(String swedishType) {
        WineType type = WINE_TYPES.get(swedishType.toLowerCase(Locale.of("sv", "SE")));
        if (type == null) {
            throw new IllegalArgumentException("Okänd vintyp i specifikationen: " + swedishType);
        }
        return type;
    }
}
