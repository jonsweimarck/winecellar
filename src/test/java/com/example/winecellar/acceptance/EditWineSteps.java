package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import static org.assertj.core.api.Assertions.assertThat;

public class EditWineSteps {

    private WineService wineService;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
    }

    @Givet("att vinet {string} finns med {int} flaskor")
    public void attVinetFinnsMedFlaskor(String name, int bottles) {
        wineService.save(StepSupport.wineWithNameAndQuantity(name, bottles));
    }

    @När("jag ändrar antalet flaskor för {string} till {int}")
    public void jagÄndrarAntaletFlaskorFörTill(String name, int newQuantity) {
        wineService.save(StepSupport.findWine(wineService, name).withQuantity(newQuantity));
    }

    @När("jag ökar antalet flaskor för {string} med {int}")
    public void jagÖkarAntaletFlaskorFörMed(String name, int amount) {
        Wine wine = StepSupport.findWine(wineService, name);
        wineService.increaseQuantityBy(wine.id(), null, amount);
    }

    @Så("ska vinet {string} visas med {int} flaskor")
    public void skaVinetVisasMedFlaskor(String name, int bottles) {
        assertThat(StepSupport.findWine(wineService, name).quantity()).isEqualTo(bottles);
    }
}
