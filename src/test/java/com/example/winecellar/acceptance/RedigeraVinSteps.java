package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import static org.assertj.core.api.Assertions.assertThat;

public class RedigeraVinSteps {

    private WineService wineService;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
    }

    @Givet("att vinet {string} finns med {int} flaskor")
    public void attVinetFinnsMedFlaskor(String namn, int flaskor) {
        wineService.save(Stegstöd.vinMedNamnOchAntal(namn, flaskor));
    }

    @När("jag ändrar antalet flaskor för {string} till {int}")
    public void jagÄndrarAntaletFlaskorFörTill(String namn, int nyttAntal) {
        wineService.save(Stegstöd.hittaVin(wineService, namn).withQuantity(nyttAntal));
    }

    @Så("ska vinet {string} visas med {int} flaskor")
    public void skaVinetVisasMedFlaskor(String namn, int flaskor) {
        assertThat(Stegstöd.hittaVin(wineService, namn).quantity()).isEqualTo(flaskor);
    }
}
