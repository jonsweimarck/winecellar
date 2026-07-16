package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class VinbilderSteps {

    private WineService wineService;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
    }

    @Givet("att vinet {string} finns utan bild")
    public void attVinetFinnsUtanBild(String namn) {
        wineService.save(Stegstöd.vinMedNamn(namn));
    }

    @När("jag laddar upp en bild av typen {string} för vinet {string}")
    public void jagLaddarUppEnBildAvTypenFörVinet(String mimeTyp, String namn) {
        byte[] bilddata = "fejkade bilddata".getBytes(StandardCharsets.UTF_8);
        wineService.save(Stegstöd.hittaVin(wineService, namn).withImage(bilddata, mimeTyp));
    }

    @Så("ska vinet {string} ha en sparad bild av typen {string}")
    public void skaVinetHaEnSparadBildAvTypen(String namn, String mimeTyp) {
        var vin = Stegstöd.hittaVin(wineService, namn);
        assertThat(vin.harBild()).isTrue();
        assertThat(vin.imageMimeType()).isEqualTo(mimeTyp);
    }
}
