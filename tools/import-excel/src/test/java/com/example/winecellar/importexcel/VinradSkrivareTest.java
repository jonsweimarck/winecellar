package com.example.winecellar.importexcel;

import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testar Wine->rad-skrivningen genom att skriva och sedan läsa tillbaka
 * samma rad med VinradParser - den mest direkta verifieringen av att
 * export verkligen är "import baklänges" (samma kolumnlayout, samma
 * tolkning åt båda hållen).
 */
class VinradSkrivareTest {

    private final VinradSkrivare skrivare = new VinradSkrivare();
    private final VinradParser parser = new VinradParser();

    @Test
    void skaÅterläsaExaktSammaVärdenSomSkrevs() {
        Wine original = Wine.builder()
                .wineType(WineType.RED)
                .country("Italien")
                .region("Sicilien")
                .subregion("Terre Siciliane")
                .grapes("85% nerello mascalese, 15% övriga druvor")
                .producer("Eduardo Torres Acosta")
                .name("Versante Nord")
                .vintage(2022)
                .purchaseDate(LocalDate.of(2024, 3, 15))
                .price(new BigDecimal("260.00"))
                .quantity(3)
                .purchaseReason("Prisvärt enligt munskänkarna")
                .tastingNotes("Ljusröd, doft av jordgubbe.")
                .ownRating(Rating.R16)
                .systembolagetProductNumber("9363301")
                .systembolagetDescription("Nyanserad, kryddig smak.")
                .munskankarnaReview("Mer än prisvärt")
                .munskankarnaRating(Rating.R14_5)
                .vivinoRating(new BigDecimal("4.1"))
                .otherReference("https://example.com/vin")
                .location("Låda 2")
                .build();

        Row row = skrivTillNyRad(original);
        Wine återinläst = parser.parse(row);

        assertThat(återinläst).isEqualTo(original);
    }

    @Test
    void skaÅterläsaAllaFemVintyperKorrekt() {
        for (WineType typ : WineType.values()) {
            Wine original = minimaltVin().toBuilder().wineType(typ).build();

            Row row = skrivTillNyRad(original);
            Wine återinläst = parser.parse(row);

            assertThat(återinläst.wineType()).isEqualTo(typ);
        }
    }

    @Test
    void skaLämnaCellerTommaFörFältSomInteÄrSatta() {
        Wine minimalt = Wine.builder()
                .wineType(WineType.RED).country("Italien").producer("Antinori").name("Chianti")
                .build();

        Row row = skrivTillNyRad(minimalt);

        assertThat(row.getCell(VinradParser.COL_VIVINO)).isNull();
        assertThat(row.getCell(VinradParser.COL_INKOPSDATUM)).isNull();
        assertThat(row.getCell(VinradParser.COL_EGET_BETYG)).isNull();
    }

    /**
     * Ett vin som bara har namnet ifyllt (möjligt sedan bara namnet blev
     * obligatoriskt i webb-UI:t, se CLAUDE.md) skrivs till Excel utan
     * problem, men VinradParser hoppar över raden vid en eventuell
     * återimport - samma "ofullständig utkastrad"-hantering som redan
     * finns, inte en ny begränsning. Dokumenterar den kända avvägningen,
     * inte ett fel.
     */
    @Test
    void ettVinMedBaraNamnetSkrivsMenHoppasÖverVidÅterimport() {
        Wine minimalt = Wine.builder().name("Anteckning om ett vin").build();

        Row row = skrivTillNyRad(minimalt);

        assertThat(row.getCell(VinradParser.COL_NAMN).getStringCellValue()).isEqualTo("Anteckning om ett vin");
        assertThatThrownBy(() -> parser.parse(row))
                .isInstanceOf(VinradParser.RadSaknarObligatoriskaFältException.class);
    }

    /**
     * En riktig, minimal 1x1-PNG (inte bara godtyckliga bytes) - POI
     * läser inte bildinnehållet vid addPicture, men en verklig bild gör
     * testet en trovärdig verifiering av att den faktiska etikettdatan
     * skrivs, inte bara att metoden inte kraschar på slumpmässiga bytes.
     */
    private static final byte[] EN_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Test
    void skaBäddaInBildenAnkradIBildkolumnen() {
        Wine original = minimaltVin().toBuilder().image(EN_PIXEL_PNG).imageMimeType("image/png").build();

        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Vin");
        Row row = sheet.createRow(1);
        XSSFDrawing ritning = (XSSFDrawing) sheet.createDrawingPatriarch();
        skrivare.skriv(original, row, datumformat(workbook), ritning);

        assertThat(ritning.getShapes()).hasSize(1);
        assertThat(workbook.getAllPictures()).hasSize(1);
        assertThat(workbook.getAllPictures().get(0).getData()).isEqualTo(EN_PIXEL_PNG);
    }

    @Test
    void skaHoppaÖverBildMedOstöddMimeTypUtanAttKrascha() {
        Wine original = minimaltVin().toBuilder().image(new byte[]{1, 2, 3}).imageMimeType("image/webp").build();

        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Vin");
        Row row = sheet.createRow(1);
        XSSFDrawing ritning = (XSSFDrawing) sheet.createDrawingPatriarch();
        skrivare.skriv(original, row, datumformat(workbook), ritning);

        assertThat(ritning.getShapes()).isEmpty();
    }

    private Wine minimaltVin() {
        return Wine.builder()
                .country("Frankrike").producer("Joseph Drouhin").name("Saint-Véran")
                .build();
    }

    private Row skrivTillNyRad(Wine vin) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Vin");
        Row row = sheet.createRow(1);
        Drawing<?> ritning = sheet.createDrawingPatriarch();
        skrivare.skriv(vin, row, datumformat(workbook), ritning);
        return row;
    }

    private CellStyle datumformat(XSSFWorkbook workbook) {
        CellStyle datumformat = workbook.createCellStyle();
        datumformat.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
        return datumformat;
    }
}
