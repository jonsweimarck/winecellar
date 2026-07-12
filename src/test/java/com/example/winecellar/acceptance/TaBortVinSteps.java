package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import static org.assertj.core.api.Assertions.assertThat;

public class TaBortVinSteps {

    private WineService wineService;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
    }

    @Givet("att vinet {string} finns i källaren")
    public void attVinetFinnsIKällaren(String namn) {
        wineService.save(Stegstöd.vinMedNamn(namn));
    }

    @När("jag tar bort vinet {string}")
    public void jagTarBortVinet(String namn) {
        wineService.removeWine(Stegstöd.hittaVin(wineService, namn).id());
    }

    @Så("ska källaren inte längre innehålla {string}")
    public void skaKällarenInteLängreInnehålla(String namn) {
        assertThat(wineService.listWines()).extracting(Wine::name).doesNotContain(namn);
    }
}
