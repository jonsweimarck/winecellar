package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import com.example.winecellar.domain.WineType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testar bara webblagret: WineService/UserRepository är stubbade. Verifierar
 * dataisolation (rätt ägares WineService-anrop) och att den nedladdade
 * filens INNEHÅLL faktiskt stämmer (inte bara att ett anrop skedde) - den
 * senare delen kräver att svaret faktiskt öppnas som en xlsx-arbetsbok med
 * POI, till skillnad från WineControllerTests HTML-strängmatchningar.
 */
@WebMvcTest(ExportController.class)
@Import(SecurityConfig.class)
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WineService wineService;

    @MockBean
    private UserRepository userRepository;

    private static final UserId MIN_ANVÄNDARE_ID = new UserId(42L);

    private static final Wine BAROLO = Wine.builder()
            .id(new WineId(1L)).owner(MIN_ANVÄNDARE_ID)
            .name("Barolo").wineType(WineType.RED).producer("Pio Cesare").country("Italien")
            .vintage(2018).quantity(3).location("Låda 1")
            .build();

    private static final Wine CHABLIS = Wine.builder()
            .id(new WineId(2L)).owner(MIN_ANVÄNDARE_ID)
            .name("Chablis").wineType(WineType.WHITE).producer("Domaine X").country("Frankrike")
            .vintage(2020).quantity(2).location("Låda 3")
            .build();

    private static final Wine RIESLING_UTAN_PRODUCENT = Wine.builder()
            .id(new WineId(3L)).owner(MIN_ANVÄNDARE_ID)
            .name("Riesling").wineType(WineType.WHITE).country("Tyskland")
            .vintage(2021).quantity(1).location("Låda 4")
            .build();

    private static final Wine CHIANTI_UTAN_ÅRGÅNG = Wine.builder()
            .id(new WineId(4L)).owner(MIN_ANVÄNDARE_ID)
            .name("Chianti").wineType(WineType.RED).producer("Antinori").country("Italien")
            .quantity(6).location("Låda 5")
            .build();

    // En riktig, avkodningsbar 1x1-PNG (samma testbild som används på
    // flera andra ställen i testsviten, t.ex. LabelScanFormIT).
    private static final byte[] EN_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    private static final Wine BAROLO_MED_BILD = BAROLO.toBuilder()
            .image(EN_PIXEL_PNG).imageMimeType("image/png")
            .build();

    @BeforeEach
    void stubbaInloggadAnvändare() {
        when(userRepository.findByUsername("testperson")).thenReturn(Optional.of(
                new User(MIN_ANVÄNDARE_ID, "testperson", "hash", Instant.now())));
    }

    @Test
    void skaExporteraMinaVinerMedKorrektaFältvärden() throws Exception {
        when(wineService.listWines(MIN_ANVÄNDARE_ID)).thenReturn(List.of(BAROLO, CHABLIS));

        MvcResult result = mockMvc.perform(get("/export/xlsx").with(user("testperson")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"vinlista.xlsx\""))
                .andReturn();

        byte[] xlsx = result.getResponse().getContentAsByteArray();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheet("Vin");
            assertThat(sheet).isNotNull();

            Row header = sheet.getRow(0);
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("Namn");

            // Sorterat på namn - Barolo före Chablis.
            Row first = sheet.getRow(1);
            assertThat(first.getCell(6).getStringCellValue()).isEqualTo("Barolo");
            assertThat(first.getCell(5).getStringCellValue()).isEqualTo("Pio Cesare");
            assertThat(first.getCell(7).getNumericCellValue()).isEqualTo(2018.0);

            Row second = sheet.getRow(2);
            assertThat(second.getCell(6).getStringCellValue()).isEqualTo("Chablis");

            assertThat(sheet.getRow(3)).isNull();
        }
    }

    @Test
    void skaExporteraBaraDenInloggadeAnvändarensViner() throws Exception {
        when(wineService.listWines(MIN_ANVÄNDARE_ID)).thenReturn(List.of(BAROLO));

        mockMvc.perform(get("/export/xlsx").with(user("testperson")))
                .andExpect(status().isOk());

        verify(wineService).listWines(eq(MIN_ANVÄNDARE_ID));
    }

    @Test
    void skaNekaUtanInloggning() throws Exception {
        mockMvc.perform(get("/export/xlsx"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void skaExporteraEnZipMedBaraVinerSomHarBild() throws Exception {
        when(wineService.listWines(MIN_ANVÄNDARE_ID)).thenReturn(List.of(BAROLO_MED_BILD, CHABLIS));

        MvcResult result = mockMvc.perform(get("/export/bilder.zip").with(user("testperson")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"vinbilder.zip\""))
                .andReturn();

        byte[] zipBytes = result.getResponse().getContentAsByteArray();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zip.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("Pio Cesare_Barolo_2018.png");
            assertThat(zip.readAllBytes()).isEqualTo(EN_PIXEL_PNG);

            assertThat(zip.getNextEntry()).isNull();
        }
    }

    @Test
    void skaNekaZipUtanInloggning() throws Exception {
        mockMvc.perform(get("/export/bilder.zip"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void skaExporteraZipMedPartiellIdentitetNärProducentSaknas() throws Exception {
        Wine rieslingMedBild = RIESLING_UTAN_PRODUCENT.toBuilder()
                .image(EN_PIXEL_PNG).imageMimeType("image/png")
                .build();
        when(wineService.listWines(MIN_ANVÄNDARE_ID)).thenReturn(List.of(rieslingMedBild));

        MvcResult result = mockMvc.perform(get("/export/bilder.zip").with(user("testperson")))
                .andExpect(status().isOk())
                .andReturn();

        byte[] zipBytes = result.getResponse().getContentAsByteArray();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zip.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("Riesling_2021.png");
            assertThat(zip.readAllBytes()).isEqualTo(EN_PIXEL_PNG);

            assertThat(zip.getNextEntry()).isNull();
        }
    }

    @Test
    void skaExporteraZipMedPartiellIdentitetNärÅrgångSaknas() throws Exception {
        Wine chiantiMedBild = CHIANTI_UTAN_ÅRGÅNG.toBuilder()
                .image(EN_PIXEL_PNG).imageMimeType("image/png")
                .build();
        when(wineService.listWines(MIN_ANVÄNDARE_ID)).thenReturn(List.of(chiantiMedBild));

        MvcResult result = mockMvc.perform(get("/export/bilder.zip").with(user("testperson")))
                .andExpect(status().isOk())
                .andReturn();

        byte[] zipBytes = result.getResponse().getContentAsByteArray();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zip.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("Antinori_Chianti.png");
            assertThat(zip.readAllBytes()).isEqualTo(EN_PIXEL_PNG);

            assertThat(zip.getNextEntry()).isNull();
        }
    }
}
