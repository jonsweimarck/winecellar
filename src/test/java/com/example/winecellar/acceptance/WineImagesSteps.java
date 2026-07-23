package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class WineImagesSteps {

    private WineService wineService;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
    }

    @Givet("att vinet {string} finns utan bild")
    public void attVinetFinnsUtanBild(String name) {
        wineService.save(StepSupport.wineWithName(name));
    }

    @När("jag laddar upp en bild av typen {string} för vinet {string}")
    public void jagLaddarUppEnBildAvTypenFörVinet(String mimeType, String name) {
        byte[] imageData = "fejkade bilddata".getBytes(StandardCharsets.UTF_8);
        wineService.save(StepSupport.findWine(wineService, name).withImage(imageData, mimeType));
    }

    @Så("ska vinet {string} ha en sparad bild av typen {string}")
    public void skaVinetHaEnSparadBildAvTypen(String name, String mimeType) {
        var wine = StepSupport.findWine(wineService, name);
        assertThat(wine.hasImage()).isTrue();
        assertThat(wine.imageMimeType()).isEqualTo(mimeType);
    }
}
