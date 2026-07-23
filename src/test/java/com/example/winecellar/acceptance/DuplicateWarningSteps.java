package com.example.winecellar.acceptance;

import com.example.winecellar.application.DuplicateCheck;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import static org.assertj.core.api.Assertions.assertThat;

public class DuplicateWarningSteps {

    private WineService wineService;
    private DuplicateCheck lastCheck;
    private Wine pendingCandidate;

    @Before
    public void setUp() {
        wineService = new WineService(new InMemoryWineRepository());
    }

    @Givet("att vinet {string} med producent {string} och årgång {int} finns med {int} flaskor")
    public void attVinetMedProducentOchÅrgångFinnsMedFlaskor(String name, String producer, int vintage, int bottles) {
        wineService.save(Wine.builder()
                .name(name).producer(producer).vintage(vintage).quantity(bottles)
                .build());
    }

    @När("jag försöker lägga till ett vin med namn {string}, producent {string} och årgång {int}")
    public void jagFörsökerLäggaTillEttVinMedNamnProducentOchÅrgång(String name, String producer, int vintage) {
        attempt(Wine.builder().name(name).producer(producer).vintage(vintage).build());
    }

    @När("jag försöker lägga till ett vin med bara namnet {string}")
    public void jagFörsökerLäggaTillEttVinMedBaraNamnet(String name) {
        attempt(Wine.builder().name(name).build());
    }

    @När("jag ändrar årgången till {int} och försöker lägga till vinet igen")
    public void jagÄndrarÅrgångenTillOchFörsökerLäggaTillVinetIgen(int newVintage) {
        attempt(pendingCandidate.toBuilder().vintage(newVintage).build());
    }

    private void attempt(Wine candidate) {
        lastCheck = wineService.checkForDuplicate(candidate);
        if (lastCheck instanceof DuplicateCheck.NoDuplicate) {
            wineService.save(candidate);
            pendingCandidate = null;
        } else {
            pendingCandidate = candidate;
        }
    }

    @Så("ska appen upptäcka en fullständig dubblett av {string}")
    public void skaAppenUpptäckaEnFullständigDubblettAv(String name) {
        assertThat(lastCheck).isInstanceOf(DuplicateCheck.FullDuplicate.class);
        assertThat(((DuplicateCheck.FullDuplicate) lastCheck).existing().name()).isEqualTo(name);
    }

    @Så("ska appen upptäcka en möjlig dubblett av {string}")
    public void skaAppenUpptäckaEnMöjligDubblettAv(String name) {
        assertThat(lastCheck).isInstanceOf(DuplicateCheck.PartialDuplicate.class);
        assertThat(((DuplicateCheck.PartialDuplicate) lastCheck).existing().name()).isEqualTo(name);
    }

    @Så("ska appen inte upptäcka någon dubblett")
    public void skaAppenInteUpptäckaNågonDubblett() {
        assertThat(lastCheck).isInstanceOf(DuplicateCheck.NoDuplicate.class);
    }

    @När("jag väljer att öka antalet på den befintliga dubbletten")
    public void jagVäljerAttÖkaAntaletPåDenBefintligaDubbletten() {
        increaseQuantityOfMatch();
    }

    @När("jag väljer att öka antalet på den möjliga dubbletten")
    public void jagVäljerAttÖkaAntaletPåDenMöjligaDubbletten() {
        increaseQuantityOfMatch();
    }

    private void increaseQuantityOfMatch() {
        wineService.increaseQuantity(existingFromLastCheck().id());
        pendingCandidate = null;
    }

    private Wine existingFromLastCheck() {
        if (lastCheck instanceof DuplicateCheck.FullDuplicate full) {
            return full.existing();
        }
        if (lastCheck instanceof DuplicateCheck.PartialDuplicate partial) {
            return partial.existing();
        }
        throw new IllegalStateException("Ingen dubblett att öka antalet på");
    }

    @När("jag väljer att lägga till vinet som nytt ändå")
    public void jagVäljerAttLäggaTillVinetSomNyttÄndå() {
        wineService.save(pendingCandidate);
        pendingCandidate = null;
    }

    @Så("ska vinet {string} nu ha {int} flaskor")
    public void skaVinetNuHaFlaskor(String name, int bottles) {
        assertThat(StepSupport.findWine(wineService, name).quantity()).isEqualTo(bottles);
    }

    @Så("källaren ska innehålla totalt {int} vin")
    public void skaKällarenInnehållaTotaltVin(int count) {
        assertThat(wineService.listWines()).hasSize(count);
    }

    @Så("källaren ska innehålla totalt {int} viner")
    public void skaKällarenInnehållaTotaltViner(int count) {
        assertThat(wineService.listWines()).hasSize(count);
    }
}
