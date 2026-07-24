package com.example.winecellar.acceptance;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.infrastructure.JpaWineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Till skillnad från de övriga stegklasserna körs dessa mot Spring-hanterade
 * bönor (WineService/JpaWineRepository) och en riktig Postgres via
 * Testcontainers - se CucumberSpringConfiguration. Har utökats till att
 * även täcka sökning (se sokning-mot-postgres.feature) - samma skäl som
 * omstartsscenariot: JpaWineRepositorys native query kan bete sig
 * annorlunda än InMemoryWineRepository (WINE-10 visade det, se CLAUDE.md).
 */
public class PersistenceSteps {

    @Autowired
    private WineService wineService;

    @Autowired
    private JpaWineRepository wineRepository;

    @Autowired
    private EntityManager entityManager;

    private List<Wine> sökresultat;

    @Before
    public void reset() {
        wineRepository.deleteAll();
    }

    @Givet("att vinet {string} är sparat i källaren")
    public void attVinetÄrSparatIKällaren(String name) {
        wineService.save(StepSupport.wineWithName(name));
    }

    @Givet("att vinet {string} med druvan {string} är sparat i källaren")
    public void attVinetMedDruvanÄrSparatIKällaren(String name, String druva) {
        wineService.save(StepSupport.wineWithName(name).toBuilder().grapes(druva).build());
    }

    @När("applikationen startas om")
    public void applikationenStartasOm() {
        entityManager.clear();
    }

    @När("jag söker efter {string} mot databasen")
    public void jagSökerEfterMotDatabasen(String sökord) {
        sökresultat = wineRepository.search(sökord);
    }

    @Så("ska vinet {string} fortfarande finnas i källaren")
    public void skaVinetFortfarandeFinnasIKällaren(String name) {
        assertThat(wineService.listWines()).anySatisfy(wine -> assertThat(wine.name()).isEqualTo(name));
    }

    @Så("ska vinet {string} finnas i sökresultatet")
    public void skaVinetFinnasISökresultatet(String name) {
        assertThat(sökresultat).anySatisfy(wine -> assertThat(wine.name()).isEqualTo(name));
    }
}
