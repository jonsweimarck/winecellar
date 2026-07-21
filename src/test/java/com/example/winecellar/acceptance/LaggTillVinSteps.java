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

public class LaggTillVinSteps {

    private static final Map<String, WineType> VINTYPER = Map.of(
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
        Map<String, String> uppgifter = dataTable.asMap(String.class, String.class);
        Wine nyttVin = Wine.builder()
                .name(uppgifter.get("namn"))
                .wineType(vinTypFrån(uppgifter.get("typ")))
                .producer(uppgifter.get("producent"))
                .country(uppgifter.get("land"))
                .vintage(Integer.parseInt(uppgifter.get("årgång")))
                .quantity(Integer.parseInt(uppgifter.get("flaskor")))
                .location(uppgifter.get("plats"))
                .build();
        wineService.save(nyttVin);
    }

    @När("jag lägger till ett vin med bara namnet {string}")
    public void jagLäggerTillEttVinMedBaraNamnet(String namn) {
        wineService.save(Wine.builder().name(namn).build());
    }

    @Så("ska källaren innehålla {int} vin")
    public void skaKällarenInnehålla(int antal) {
        assertThat(wineService.listWines()).hasSize(antal);
    }

    @Och("vinet {string} ska visas med {int} flaskor i {string}")
    public void vinetSkaVisasMedFlaskorI(String namn, int flaskor, String plats) {
        Wine vin = Stegstöd.hittaVin(wineService, namn);
        assertThat(vin.quantity()).isEqualTo(flaskor);
        assertThat(vin.location()).isEqualTo(plats);
    }

    @Och("vinet {string} ska sakna övriga uppgifter")
    public void vinetSkaSaknaÖvrigaUppgifter(String namn) {
        Wine vin = Stegstöd.hittaVin(wineService, namn);
        assertThat(vin.wineType()).isNull();
        assertThat(vin.producer()).isNull();
        assertThat(vin.country()).isNull();
        assertThat(vin.vintage()).isNull();
        assertThat(vin.quantity()).isNull();
        assertThat(vin.location()).isNull();
    }

    private WineType vinTypFrån(String svenskTyp) {
        WineType typ = VINTYPER.get(svenskTyp.toLowerCase(Locale.of("sv", "SE")));
        if (typ == null) {
            throw new IllegalArgumentException("Okänd vintyp i specifikationen: " + svenskTyp);
        }
        return typ;
    }
}
