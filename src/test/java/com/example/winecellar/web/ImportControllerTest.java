package com.example.winecellar.web;

import com.example.winecellar.application.DuplicateCheck;
import com.example.winecellar.application.ImportPreviewService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testar bara webblagret: WineService/UserRepository är stubbade (via
 * ImportPreviewService, som är en riktig Spring-hanterad böna här -
 * bara dess beroende WineService mockas, precis som
 * `LabelScanFormIT`/`WineControllerTest`s val att mocka på olika nivåer
 * beroende på vad som testas, se de klassernas kommentarer). Bygger en
 * riktig, minimal xlsx-fil i minnet med POI istället för att checka in
 * en testfil - fyra rader: en utan namn (hoppas över), en som mockas
 * som fullständig dubblett, en som partiell, en ren.
 */
@WebMvcTest(ImportController.class)
@Import({SecurityConfig.class, ImportPreviewService.class})
class ImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WineService wineService;

    @MockBean
    private UserRepository userRepository;

    private static final UserId MIN_ANVÄNDARE_ID = new UserId(42L);

    // Redan sparade viner (med riktiga id:n) som "Barolo"/"Chianti"-raderna
    // i testfilen mockas matcha - avgörande att dessa har ett `id`, till
    // skillnad från de nytolkade kandidaterna (som WineRowParser aldrig
    // sätter id på) - annars blir `existing.id()` null och
    // `increaseQuantityBy`-verifieringarna nedan meningslösa.
    //
    // BAROLO_EXISTING_QUANTITY/BAROLO_ROW_QUANTITY speglar exakt WINE-28s
    // felrapport (2 befintliga + 2 importerade ska ge 4, inte 3) - se
    // skaOkaAntalVidFullständigDubblettMedStrategiÖkaAntal.
    private static final int BAROLO_EXISTING_QUANTITY = 2;
    private static final int BAROLO_ROW_QUANTITY = 2;
    private static final int CHIANTI_ROW_QUANTITY = 4;
    private static final int RIOJA_ROW_QUANTITY = 5;

    private static final Wine EXISTING_BAROLO = Wine.builder()
            .id(new Wine.WineId(101L)).owner(MIN_ANVÄNDARE_ID)
            .name("Barolo").producer("Pio Cesare").vintage(2018).quantity(BAROLO_EXISTING_QUANTITY)
            .build();

    private static final Wine EXISTING_CHIANTI = Wine.builder()
            .id(new Wine.WineId(102L)).owner(MIN_ANVÄNDARE_ID)
            .name("Chianti").producer("Antinori").vintage(2019).quantity(1)
            .build();

    @BeforeEach
    void stubbaInloggadAnvändare() {
        when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                new User(MIN_ANVÄNDARE_ID, "testperson", "hash", Instant.now())));
    }

    private void stubbaDubblettkontroll() {
        when(wineService.checkForDuplicate(any(), eq(MIN_ANVÄNDARE_ID))).thenAnswer(invocation -> {
            Wine candidate = invocation.getArgument(0);
            return switch (candidate.name()) {
                case "Barolo" -> new DuplicateCheck.FullDuplicate(EXISTING_BAROLO);
                case "Chianti" -> new DuplicateCheck.PartialDuplicate(EXISTING_CHIANTI);
                default -> new DuplicateCheck.NoDuplicate();
            };
        });
    }

    /** Kör torrkörningen (POST /import) och returnerar sessionen så commit-steget kan återanvända den. */
    private MockHttpSession körTorrkörning(MockMultipartFile... bilder) throws Exception {
        return körTorrkörning(new MockHttpSession(), bilder);
    }

    /**
     * Samma sak, men i en REDAN existerande session - används för att
     * verifiera att en andra torrkörning i samma session städar bort den
     * första, ej committerade temp-mappen (WINE-27) istället för att bara
     * skriva över sökvägen i sessionen och lämna den övergiven på disk.
     */
    private MockHttpSession körTorrkörning(MockHttpSession session, MockMultipartFile... bilder) throws Exception {
        byte[] xlsx = enkelXlsxMedFyraRader();
        MockMultipartFile fil = new MockMultipartFile("fil", "vinlista.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        MockMultipartHttpServletRequestBuilder builder = multipart("/import").file(fil);
        for (MockMultipartFile bild : bilder) {
            builder = builder.file(bild);
        }

        MvcResult result = mockMvc.perform(builder.session(session).with(user("testperson")).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    void skaVisaFormulärUtanInloggningsfriKrasch() throws Exception {
        mockMvc.perform(get("/import")
                        .with(user("testperson")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Importera vinlista")));
    }

    @Test
    void skaNekaFormuläretUtanInloggning() throws Exception {
        mockMvc.perform(get("/import"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void skaVisaRättSammanfattningOchAldrigSparaNågot() throws Exception {
        stubbaDubblettkontroll();

        byte[] xlsx = enkelXlsxMedFyraRader();
        MockMultipartFile fil = new MockMultipartFile("fil", "vinlista.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        mockMvc.perform(multipart("/import").file(fil).with(user("testperson")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rader totalt")));

        verify(wineService, never()).save(any());
    }

    /**
     * WINE-27: en andra torrkörning i samma session (t.ex. användaren
     * laddade upp fel fil och försöker igen, utan att committa den första)
     * ska städa bort den första temp-mappen, inte bara skriva över
     * sökvägen i sessionen och lämna mappen övergiven på disk för alltid.
     */
    @Test
    void skaStädaBortTidigareOkommitteradTempMappVidNyTorrkörning() throws Exception {
        stubbaDubblettkontroll();
        MockHttpSession session = körTorrkörning();
        String förstaSökvägen = (String) session.getAttribute(ImportController.SESSION_KEY_PENDING_IMPORT_PATH);
        assertThat(Files.exists(Path.of(förstaSökvägen))).isTrue();

        körTorrkörning(session);

        String andraSökvägen = (String) session.getAttribute(ImportController.SESSION_KEY_PENDING_IMPORT_PATH);
        assertThat(andraSökvägen).isNotEqualTo(förstaSökvägen);
        assertThat(Files.exists(Path.of(förstaSökvägen))).isFalse();
    }

    /**
     * Reproducerar WINE-28 exakt: Barolo finns med 2 flaskor
     * (BAROLO_EXISTING_QUANTITY), importraden anger också 2
     * (BAROLO_ROW_QUANTITY). Den gamla buggen anropade increaseQuantity
     * (alltid +1) rakt av, vilket hade gett 2+1=3 - rätt anrop är
     * increaseQuantityBy(..., 2), vilket (verifierat separat i
     * WineService-nivåns Cucumber-scenario) faktiskt ger 2+2=4.
     */
    @Test
    void skaOkaAntalVidFullständigDubblettMedStrategiÖkaAntal() throws Exception {
        stubbaDubblettkontroll();
        MockHttpSession session = körTorrkörning();

        mockMvc.perform(post("/import/commit")
                        .session(session)
                        .param("fullDuplicateStrategy", "OKA_ANTAL")
                        .param("partialDuplicateStrategy", "HOPPA_OVER")
                        .with(user("testperson")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/import"));

        verify(wineService).increaseQuantityBy(eq(EXISTING_BAROLO.id()), eq(MIN_ANVÄNDARE_ID), eq(BAROLO_ROW_QUANTITY));
        // "Rioja" är en ren rad (ingen dubblett) i testfilen - sparas alltid,
        // oavsett vilken dubblettstrategi som väljs för de andra raderna.
        ArgumentCaptor<Wine> captor = ArgumentCaptor.forClass(Wine.class);
        verify(wineService).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Rioja");
    }

    @Test
    void skaSkapaNyttVinVidPartiellDubblettMedStrategiLäggTillSomNytt() throws Exception {
        stubbaDubblettkontroll();
        MockHttpSession session = körTorrkörning();

        mockMvc.perform(post("/import/commit")
                        .session(session)
                        .param("fullDuplicateStrategy", "HOPPA_OVER")
                        .param("partialDuplicateStrategy", "LAGG_TILL_SOM_NYTT")
                        .with(user("testperson")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Wine> captor = ArgumentCaptor.forClass(Wine.class);
        verify(wineService, never()).increaseQuantityBy(eq(EXISTING_CHIANTI.id()), any(), anyInt());
        // Två sparade: "Chianti" (partiell dubblett, lägg till som nytt) och
        // "Rioja" (ren rad, sparas alltid oavsett strategi).
        verify(wineService, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Wine::name).containsExactlyInAnyOrder("Chianti", "Rioja");
        assertThat(captor.getAllValues()).allSatisfy(saved -> assertThat(saved.owner()).isEqualTo(MIN_ANVÄNDARE_ID));
    }

    /**
     * De tre återstående dubblettstrategi-kombinationerna (WINE-26) - inte
     * täckta av WINE-25s egna två tester ovan. Ligger som MockMvc-tester,
     * inte ett Cucumber-scenario mot applikationslagret: själva
     * strategivalet (vilken WineService-metod som anropas för respektive
     * dubbletttyp) är webblagrets orkestrering, inte en applikationslagers-
     * regel - `ImportController` gör medvetet samma sak som `WineController`
     * redan gör för den enskilda dubblettvarningen (ADR-mönstret från
     * WINE-25: ingen egen application-tjänst för commit-strategin).
     */
    @Test
    void skaHoppaÖverVidFullständigDubblettMedStrategiHoppaÖver() throws Exception {
        stubbaDubblettkontroll();
        MockHttpSession session = körTorrkörning();

        mockMvc.perform(post("/import/commit")
                        .session(session)
                        .param("fullDuplicateStrategy", "HOPPA_OVER")
                        .param("partialDuplicateStrategy", "HOPPA_OVER")
                        .with(user("testperson")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(wineService, never()).increaseQuantityBy(eq(EXISTING_BAROLO.id()), any(), anyInt());
        // Bara "Rioja" (ren rad) sparas - Barolo (full dubblett) och
        // Chianti (partiell dubblett) hoppas båda över.
        ArgumentCaptor<Wine> captor = ArgumentCaptor.forClass(Wine.class);
        verify(wineService).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Rioja");
    }

    @Test
    void skaOkaAntalVidPartiellDubblettMedStrategiÖkaAntal() throws Exception {
        stubbaDubblettkontroll();
        MockHttpSession session = körTorrkörning();

        mockMvc.perform(post("/import/commit")
                        .session(session)
                        .param("fullDuplicateStrategy", "HOPPA_OVER")
                        .param("partialDuplicateStrategy", "OKA_ANTAL")
                        .with(user("testperson")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(wineService).increaseQuantityBy(eq(EXISTING_CHIANTI.id()), eq(MIN_ANVÄNDARE_ID), eq(CHIANTI_ROW_QUANTITY));
        ArgumentCaptor<Wine> captor = ArgumentCaptor.forClass(Wine.class);
        verify(wineService).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Rioja");
    }

    /**
     * Isolerar partiell-hoppa-över från fullständig-hoppa-över (till
     * skillnad från det första testet ovan, som råkar sätta båda
     * strategierna till HOPPA_OVER samtidigt) - full dubblett väljer HÄR
     * "öka antal" medan partiell väljer "hoppa över", för att bekräfta att
     * de två inställningarna verkligen är oberoende av varandra.
     */
    @Test
    void skaHoppaÖverVidPartiellDubblettMedStrategiHoppaÖver() throws Exception {
        stubbaDubblettkontroll();
        MockHttpSession session = körTorrkörning();

        mockMvc.perform(post("/import/commit")
                        .session(session)
                        .param("fullDuplicateStrategy", "OKA_ANTAL")
                        .param("partialDuplicateStrategy", "HOPPA_OVER")
                        .with(user("testperson")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(wineService).increaseQuantityBy(eq(EXISTING_BAROLO.id()), eq(MIN_ANVÄNDARE_ID), eq(BAROLO_ROW_QUANTITY));
        verify(wineService, never()).increaseQuantityBy(eq(EXISTING_CHIANTI.id()), any(), anyInt());
        ArgumentCaptor<Wine> captor = ArgumentCaptor.forClass(Wine.class);
        verify(wineService).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Rioja");
    }

    @Test
    void skaKopplaBildTillNyttVinEnligtNamnkonventionen() throws Exception {
        stubbaDubblettkontroll();
        MockMultipartFile bild = new MockMultipartFile(
                "bilder", "Muga_Rioja_2020.jpg", "image/jpeg", new byte[] {9, 9, 9});
        MockHttpSession session = körTorrkörning(bild);

        mockMvc.perform(post("/import/commit")
                        .session(session)
                        .param("fullDuplicateStrategy", "HOPPA_OVER")
                        .param("partialDuplicateStrategy", "HOPPA_OVER")
                        .with(user("testperson")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Wine> captor = ArgumentCaptor.forClass(Wine.class);
        verify(wineService).save(captor.capture());
        Wine saved = captor.getValue();
        assertThat(saved.name()).isEqualTo("Rioja");
        assertThat(saved.image()).isEqualTo(new byte[] {9, 9, 9});
        assertThat(saved.imageMimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void skaKopplaBildTillNyttVinMedPartiellIdentitet() throws Exception {
        stubbaDubblettkontroll();
        byte[] xlsx = xlsxMedEnRadUtanÅrgång();
        MockMultipartFile fil = new MockMultipartFile("fil", "vinlista.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);
        MockMultipartFile bild = new MockMultipartFile(
                "bilder", "Muga_Rioja.jpg", "image/jpeg", new byte[] {8, 8, 8});

        MvcResult result = mockMvc.perform(multipart("/import").file(fil).file(bild)
                        .with(user("testperson")).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();

        mockMvc.perform(post("/import/commit")
                        .session(session)
                        .param("fullDuplicateStrategy", "HOPPA_OVER")
                        .param("partialDuplicateStrategy", "HOPPA_OVER")
                        .with(user("testperson")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Wine> captor = ArgumentCaptor.forClass(Wine.class);
        verify(wineService).save(captor.capture());
        Wine saved = captor.getValue();
        assertThat(saved.name()).isEqualTo("Rioja");
        assertThat(saved.producer()).isEqualTo("Muga");
        assertThat(saved.vintage()).isNull();
        assertThat(saved.image()).isEqualTo(new byte[] {8, 8, 8});
        assertThat(saved.imageMimeType()).isEqualTo("image/jpeg");
    }

    /**
     * Fyra rader, samma kolumnlayout som `WineRowParser`/README:s
     * Datamodell-avsnitt (A=vintyp ... G=namn, H=årgång ... K=antal):
     * "Barolo" (fullständig identitet, mockas som fullständig dubblett),
     * "Chianti" (mockas som partiell dubblett), "Rioja" (ren, ny), och
     * en rad helt utan namn (hoppas över av WineRowParser självt).
     * Antal är obligatoriskt sedan ADR 0016 - varje riktig rad måste
     * alltså ha en ifylld antal-cell, annars hoppas den också över.
     */
    private byte[] enkelXlsxMedFyraRader() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Vin");
            skrivRad(sheet, 0, "Rubrik", "", "", 0, 0);
            skrivRad(sheet, 1, "Barolo", "Pio Cesare", "Rött", 2018, BAROLO_ROW_QUANTITY);
            skrivRad(sheet, 2, "Chianti", "Antinori", "Rött", 2019, CHIANTI_ROW_QUANTITY);
            skrivRad(sheet, 3, "Rioja", "Muga", "Rött", 2020, RIOJA_ROW_QUANTITY);
            Row utanNamn = sheet.createRow(4);
            utanNamn.createCell(5).setCellValue("Okänd producent utan namn");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] xlsxMedEnRadUtanÅrgång() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Vin");
            skrivRad(sheet, 0, "Rubrik", "", "", 0, 0);
            skrivRad(sheet, 1, "Rioja", "Muga", "Rött", 0, RIOJA_ROW_QUANTITY);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void skrivRad(Sheet sheet, int rowNum, String name, String producer, String wineType, int vintage, int quantity) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(wineType);
        row.createCell(5).setCellValue(producer);
        row.createCell(6).setCellValue(name);
        if (vintage > 0) {
            row.createCell(7).setCellValue(vintage);
        }
        if (quantity > 0) {
            row.createCell(10).setCellValue(quantity);
        }
    }
}
