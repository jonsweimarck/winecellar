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

public class ListWinesSteps {

    private WineService wineService;
    private List<Wine> shownList;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
    }

    @Givet("att källaren innehåller vinerna {string} och {string}")
    public void attKällarenInnehållerVinerna(String name1, String name2) {
        wineService.save(StepSupport.wineWithName(name1));
        wineService.save(StepSupport.wineWithName(name2));
    }

    @När("jag visar vinlistan")
    public void jagVisarVinlistan() {
        shownList = wineService.listWines();
    }

    @Så("ska listan innehålla {string} och {string}")
    public void skaListanInnehålla(String name1, String name2) {
        assertThat(shownList).extracting(Wine::name).contains(name1, name2);
    }
}
