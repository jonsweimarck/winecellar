package com.example.winecellar.acceptance;

import com.example.winecellar.application.ImportPreview;
import com.example.winecellar.application.ImportPreviewService;
import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.application.RowCandidate;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WINE-38: Cucumber-scenarier för importfel, mot applikationslagret
 * (ImportPreviewService). Körs mot Spring-hanterade bönor och en riktig
 * Postgres via Testcontainers, samma mönster som PersistenceSteps.
 */
public class ImportSteps {

    @Autowired
    private WineService wineService;

    @Autowired
    private ImportPreviewService importPreviewService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    private static final String TESTKONTO_ANVÄNDARNAMN = "importStepsTest";
    private static final String TESTKONTO_LÖSENORD = "testlösenord123";

    private UserId ägare;
    private ImportPreview senastePreview;

    /**
     * `order = 2` - efter att PersistenceSteps har raderat viner
     * ({@code order = -1}) och RegistrationSteps har raderat users
     * ({@code order = 0}) registrerat sitt testkonto ({@code order = 1}).
     */
    @Before(order = 2)
    public void registreraTestkonto() {
        registrationService.register(TESTKONTO_ANVÄNDARNAMN, TESTKONTO_LÖSENORD);
        ägare = userRepository.findByUsername(TESTKONTO_ANVÄNDARNAMN).orElseThrow().id();
    }

    @Givet("att vinet {string} med producent {string} och årgång {int} finns med {int} flaskor i källaren")
    public void attVinetMedProducentOchÅrgångFinnsMedFlaskorIKällaren(
            String name, String producer, int vintage, int bottles) {
        wineService.save(Wine.builder()
                .name(name).producer(producer).vintage(vintage).quantity(bottles)
                .owner(ägare)
                .build());
    }

    @När("jag förhandsgranskar en importfil med följande rader")
    public void jagFörhandsgranskarEnImportfilMedFöljandeRader(DataTable table) {
        List<Map<String, String>> rader = table.asMaps();
        List<RowCandidate> candidates = new ArrayList<>();
        for (Map<String, String> rad : rader) {
            int rowNumber = Integer.parseInt(rad.get("rad"));
            String name = rad.get("namn");
            String producer = rad.get("producent");
            String vintageStr = rad.get("årgång");
            Integer vintage = vintageStr == null || vintageStr.isBlank() ? null : Integer.parseInt(vintageStr);

            Wine.Builder builder = Wine.builder().name(name).quantity(1);
            if (producer != null && !producer.isBlank()) {
                builder.producer(producer);
            }
            if (vintage != null) {
                builder.vintage(vintage);
            }
            candidates.add(new RowCandidate(rowNumber, builder.build()));
        }
        senastePreview = importPreviewService.preview(candidates, List.of(), ägare);
    }

    @Så("ska rad {int} rapporteras som en fullständig dubblett till ett befintligt vin")
    public void skaRadRapporterasSomEnFullständigDubblettTillEttBefintligtVin(int rowNumber) {
        assertThat(senastePreview.issues()).anySatisfy(issue -> {
            assertThat(issue.rowNumber()).isEqualTo(rowNumber);
            assertThat(issue.message()).contains("fullständig dubblett");
        });
    }

    @Så("ska rad {int} rapporteras som en möjlig dubblett till ett befintligt vin")
    public void skaRadRapporterasSomEnMöjligDubblettTillEttBefintligtVin(int rowNumber) {
        assertThat(senastePreview.issues()).anySatisfy(issue -> {
            assertThat(issue.rowNumber()).isEqualTo(rowNumber);
            assertThat(issue.message()).contains("möjlig dubblett");
        });
    }

    @Så("antalet nya viner i förhandsgranskningen ska vara {int}")
    public void antaletNyaVinerIFörhandsgranskningenSkaVara(int count) {
        assertThat(senastePreview.clean()).isEqualTo(count);
    }
}
