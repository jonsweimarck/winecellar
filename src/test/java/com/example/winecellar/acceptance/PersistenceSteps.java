package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.infrastructure.JpaWineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Till skillnad från de övriga stegklasserna körs dessa mot Spring-hanterade
 * bönor (WineService/JpaWineRepository) och en riktig Postgres via
 * Testcontainers - se CucumberSpringConfiguration.
 */
public class PersistenceSteps {

    @Autowired
    private WineService wineService;

    @Autowired
    private JpaWineRepository wineRepository;

    @Autowired
    private EntityManager entityManager;

    @Before
    public void reset() {
        wineRepository.deleteAll();
    }

    @Givet("att vinet {string} är sparat i källaren")
    public void attVinetÄrSparatIKällaren(String name) {
        wineService.save(StepSupport.wineWithName(name));
    }

    @När("applikationen startas om")
    public void applikationenStartasOm() {
        entityManager.clear();
    }

    @Så("ska vinet {string} fortfarande finnas i källaren")
    public void skaVinetFortfarandeFinnasIKällaren(String name) {
        assertThat(wineService.listWines()).anySatisfy(wine -> assertThat(wine.name()).isEqualTo(name));
    }
}
