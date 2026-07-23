package com.example.winecellar.acceptance;

import com.example.winecellar.application.InterpretedLabel;
import com.example.winecellar.application.LabelInterpretationResult;
import com.example.winecellar.application.LabelInterpretationService;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import static org.assertj.core.api.Assertions.assertThat;

public class ScanLabelSteps {

    private FakeLabelInterpreter labelInterpreter;
    private LabelInterpretationService labelInterpretationService;
    private LabelInterpretationResult result;

    @Before
    public void setUp() {
        labelInterpreter = new FakeLabelInterpreter();
        labelInterpretationService = new LabelInterpretationService(labelInterpreter);
    }

    @Givet("att etikettolkningen ger namn {string}, producent {string}, årgång {int}, land {string} och region {string}")
    public void attEtikettolkningenGerNamnProducentÅrgångLandOchRegion(
            String name, String producer, int vintage, String country, String region) {
        labelInterpreter.willReturn(new InterpretedLabel(name, producer, vintage, country, region));
    }

    @Givet("att etikettolkningen bara ger namn {string}")
    public void attEtikettolkningenBaraGerNamn(String name) {
        labelInterpreter.willReturn(new InterpretedLabel(name, null, null, null, null));
    }

    @När("jag tolkar en fotograferad etikett")
    public void jagTolkarEnFotograferadEtikett() {
        result = labelInterpretationService.interpret(new byte[]{1, 2, 3}, "image/jpeg");
    }

    @Så("visas ett utkast med namn {string}, producent {string}, årgång {int}, land {string} och region {string}")
    public void visasEttUtkastMedNamnProducentÅrgångLandOchRegion(
            String name, String producer, int vintage, String country, String region) {
        LabelInterpretationResult.Interpreted interpreted = interpretedResult();
        assertThat(interpreted.draft().name()).isEqualTo(name);
        assertThat(interpreted.draft().producer()).isEqualTo(producer);
        assertThat(interpreted.draft().vintage()).isEqualTo(vintage);
        assertThat(interpreted.draft().country()).isEqualTo(country);
        assertThat(interpreted.draft().region()).isEqualTo(region);
    }

    @Så("samtliga tolkade fält är markerade som tolkade")
    public void samtligaTolkadeFältÄrMarkeradeSomTolkade() {
        assertThat(interpretedResult().interpretedFields())
                .containsExactlyInAnyOrder("name", "producer", "vintage", "country", "region");
    }

    @Så("visas ett utkast med bara namnet {string} ifyllt")
    public void visasEttUtkastMedBaraNamnetIfyllt(String name) {
        LabelInterpretationResult.Interpreted interpreted = interpretedResult();
        assertThat(interpreted.draft().name()).isEqualTo(name);
        assertThat(interpreted.draft().producer()).isNull();
        assertThat(interpreted.draft().vintage()).isNull();
        assertThat(interpreted.draft().country()).isNull();
        assertThat(interpreted.draft().region()).isNull();
        assertThat(interpreted.interpretedFields()).containsExactly("name");
    }

    private LabelInterpretationResult.Interpreted interpretedResult() {
        assertThat(result).isInstanceOf(LabelInterpretationResult.Interpreted.class);
        return (LabelInterpretationResult.Interpreted) result;
    }
}
