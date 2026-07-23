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

/**
 * Testar Wine->rad-skrivningen genom att skriva och sedan läsa tillbaka
 * samma rad med WineRowParser - den mest direkta verifieringen av att
 * export verkligen är "import baklänges" (samma kolumnlayout, samma
 * tolkning åt båda hållen).
 */
class WineRowWriterTest {

    private final WineRowWriter writer = new WineRowWriter();
    private final WineRowParser parser = new WineRowParser();

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

        Row row = writeToNewRow(original);
        Wine readBack = parser.parse(row);

        assertThat(readBack).isEqualTo(original);
    }

    @Test
    void skaÅterläsaAllaFemVintyperKorrekt() {
        for (WineType type : WineType.values()) {
            Wine original = minimalWine().toBuilder().wineType(type).build();

            Row row = writeToNewRow(original);
            Wine readBack = parser.parse(row);

            assertThat(readBack.wineType()).isEqualTo(type);
        }
    }

    @Test
    void skaLämnaCellerTommaFörFältSomInteÄrSatta() {
        Wine minimal = Wine.builder()
                .wineType(WineType.RED).country("Italien").producer("Antinori").name("Chianti")
                .build();

        Row row = writeToNewRow(minimal);

        assertThat(row.getCell(WineRowParser.COL_VIVINO)).isNull();
        assertThat(row.getCell(WineRowParser.COL_PURCHASE_DATE)).isNull();
        assertThat(row.getCell(WineRowParser.COL_OWN_RATING)).isNull();
    }

    /**
     * Ett vin som bara har namnet ifyllt (möjligt sedan bara namnet blev
     * obligatoriskt i webb-UI:t, se CLAUDE.md) skrivs till Excel och
     * återläses nu korrekt - WineRowParser kräver sedan 2026-07-22 bara
     * namnet, samma regel åt båda hållen (tidigare hoppade parsern över
     * en sådan rad vid återimport; det var den kända begränsningen som
     * ledde till den ändringen).
     */
    @Test
    void ettVinMedBaraNamnetSkrivsOchÅterlässKorrekt() {
        Wine minimal = Wine.builder().name("Anteckning om ett vin").build();

        Row row = writeToNewRow(minimal);
        Wine readBack = parser.parse(row);

        assertThat(readBack.name()).isEqualTo("Anteckning om ett vin");
        assertThat(readBack.wineType()).isNull();
        assertThat(readBack.country()).isNull();
        assertThat(readBack.producer()).isNull();
        assertThat(readBack.vintage()).isNull();
        assertThat(readBack.quantity()).isNull();
    }

    /**
     * En riktig, minimal 1x1-PNG (inte bara godtyckliga bytes) - POI
     * läser inte bildinnehållet vid addPicture, men en verklig bild gör
     * testet en trovärdig verifiering av att den faktiska etikettdatan
     * skrivs, inte bara att metoden inte kraschar på slumpmässiga bytes.
     */
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Test
    void skaBäddaInBildenAnkradIBildkolumnen() {
        Wine original = minimalWine().toBuilder().image(ONE_PIXEL_PNG).imageMimeType("image/png").build();

        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Vin");
        Row row = sheet.createRow(1);
        XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
        writer.write(original, row, dateFormat(workbook), drawing);

        assertThat(drawing.getShapes()).hasSize(1);
        assertThat(workbook.getAllPictures()).hasSize(1);
        assertThat(workbook.getAllPictures().get(0).getData()).isEqualTo(ONE_PIXEL_PNG);
    }

    @Test
    void skaHoppaÖverBildMedOstöddMimeTypUtanAttKrascha() {
        Wine original = minimalWine().toBuilder().image(new byte[]{1, 2, 3}).imageMimeType("image/webp").build();

        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Vin");
        Row row = sheet.createRow(1);
        XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
        writer.write(original, row, dateFormat(workbook), drawing);

        assertThat(drawing.getShapes()).isEmpty();
    }

    private Wine minimalWine() {
        return Wine.builder()
                .country("Frankrike").producer("Joseph Drouhin").name("Saint-Véran")
                .build();
    }

    private Row writeToNewRow(Wine wine) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Vin");
        Row row = sheet.createRow(1);
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        writer.write(wine, row, dateFormat(workbook), drawing);
        return row;
    }

    private CellStyle dateFormat(XSSFWorkbook workbook) {
        CellStyle dateFormat = workbook.createCellStyle();
        dateFormat.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
        return dateFormat;
    }
}
