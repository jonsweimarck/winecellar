package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ListaVinerSteps {

    private WineService wineService;
    private List<Wine> visadLista;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
    }

    @Givet("att källaren innehåller vinerna {string} och {string}")
    public void attKällarenInnehållerVinerna(String namn1, String namn2) {
        wineService.save(Stegstöd.vinMedNamn(namn1));
        wineService.save(Stegstöd.vinMedNamn(namn2));
    }

    @När("jag visar vinlistan")
    public void jagVisarVinlistan() {
        visadLista = wineService.listWines();
    }

    @Så("ska listan innehålla {string} och {string}")
    public void skaListanInnehålla(String namn1, String namn2) {
        assertThat(visadLista).extracting(Wine::name).contains(namn1, namn2);
    }
}
