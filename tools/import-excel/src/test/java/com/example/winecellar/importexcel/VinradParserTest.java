package com.example.winecellar.importexcel;

import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testar rad->Wine-mappningen direkt mot i-minnet-konstruerade POI-rader,
 * inte mot den riktiga Vinlista.xlsx (som inte checkas in i repot - den
 * innehåller en riktig persons vinsamling). Fokus på de knepiga delarna:
 * rubbig indata (dubbla mellanslag i betygsetiketter, prisceller med
 * extra text).
 */
class VinradParserTest {

    private final VinradParser parser = new VinradParser();

    @Test
    void skaMappaEnFullständigRadKorrekt() {
        Row row = radMed(sheet -> {
        });
        skrivCell(row, 0, "Rött");
        skrivCell(row, 1, "Italien");
        skrivCell(row, 2, "Sicilien");
        skrivCell(row, 3, "Terre Siciliane");
        skrivCell(row, 4, "85% nerello mascalese, 15% övriga druvor");
        skrivCell(row, 5, "Eduardo Torres Acosta");
        skrivCell(row, 6, "Versante Nord");
        skrivCell(row, 7, 2022);
        skrivCell(row, 9, DateUtil.getExcelDate(LocalDate.of(2024, 3, 15)));
        skrivCell(row, 10, 260);
        skrivCell(row, 11, 3);
        skrivCell(row, 12, "Prisvärt enligt munskänkarna");
        skrivCell(row, 13, "Ljusröd, doft av jordgubbe.");
        skrivCell(row, 14, "16 (15 - 17,5 Högklassigt vin)");
        skrivCell(row, 15, "9363301");
        skrivCell(row, 16, "Nyanserad, kryddig smak.");
        skrivCell(row, 17, "Mer än prisvärt\n\nNågot återhållen doft.");
        skrivCell(row, 18, "14,5 (12 - 14,5 Bra till mycket bra vin)");
        skrivCell(row, 19, 4.0999999999999996);
        skrivCell(row, 20, "https://example.com/vin");
        skrivCell(row, 21, "Låda 2");

        Wine vin = parser.parse(row);

        assertThat(vin.wineType()).isEqualTo(WineType.RED);
        assertThat(vin.country()).isEqualTo("Italien");
        assertThat(vin.region()).isEqualTo("Sicilien");
        assertThat(vin.subregion()).isEqualTo("Terre Siciliane");
        assertThat(vin.grapes()).isEqualTo("85% nerello mascalese, 15% övriga druvor");
        assertThat(vin.producer()).isEqualTo("Eduardo Torres Acosta");
        assertThat(vin.name()).isEqualTo("Versante Nord");
        assertThat(vin.vintage()).isEqualTo(2022);
        assertThat(vin.purchaseDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(vin.price()).isEqualByComparingTo("260.00");
        assertThat(vin.quantity()).isEqualTo(3);
        assertThat(vin.purchaseReason()).isEqualTo("Prisvärt enligt munskänkarna");
        assertThat(vin.tastingNotes()).isEqualTo("Ljusröd, doft av jordgubbe.");
        assertThat(vin.ownRating()).isEqualTo(Rating.R16);
        assertThat(vin.systembolagetProductNumber()).isEqualTo("9363301");
        assertThat(vin.systembolagetDescription()).isEqualTo("Nyanserad, kryddig smak.");
        assertThat(vin.munskankarnaReview()).isEqualTo("Mer än prisvärt\n\nNågot återhållen doft.");
        assertThat(vin.munskankarnaRating()).isEqualTo(Rating.R14_5);
        assertThat(vin.vivinoRating()).isEqualByComparingTo("4.1");
        assertThat(vin.otherReference()).isEqualTo("https://example.com/vin");
        assertThat(vin.location()).isEqualTo("Låda 2");
    }

    @Test
    void skaHoppaÖverRadSomSaknarNamn() {
        Row row = radMed(sheet -> {
        });
        skrivCell(row, 0, "Rött");
        skrivCell(row, 1, "Frankrike");
        skrivCell(row, 5, "Domaine Fond Moiroux");
        // Ingen namn-cell (kolumn 6) - motsvarar en ofullständig utkastrad i kalkylen.

        assertThatThrownBy(() -> parser.parse(row))
                .isInstanceOf(VinradParser.RadSaknarObligatoriskaFältException.class);
    }

    @Test
    void skaMatchaBetygMedDubblaMellanslagIKällfilen() {
        Row row = minimalRad();
        // Källfilens rad för 8,5 har dubbla mellanslag: "8,5  (6 - 8,5  Enkel vin)".
        skrivCell(row, 14, "8,5  (6 - 8,5  Enkel vin)");

        Wine vin = parser.parse(row);

        assertThat(vin.ownRating()).isEqualTo(Rating.R8_5);
    }

    @Test
    void skaTolkaPrisMedExtraAnteckningPåEgenRad() {
        Row row = minimalRad();
        skrivCell(row, 10, "329\n(2021)");

        Wine vin = parser.parse(row);

        assertThat(vin.price()).isEqualByComparingTo("329.00");
    }

    @Test
    void skaLämnaSystembolagetsBeskrivningNullOmDenSaknasMenAnvändaProduktnummerkolumnen() {
        Row row = minimalRad();
        skrivCell(row, 15, "5020201");
        // Kolumn 16 (beskrivningen) lämnas tom.

        Wine vin = parser.parse(row);

        assertThat(vin.systembolagetProductNumber()).isEqualTo("5020201");
        assertThat(vin.systembolagetDescription()).isNull();
    }

    @Test
    void skaKastaTydligtFelOmBetygetInteMatcharNågotAvDe29Kända() {
        Row row = minimalRad();
        skrivCell(row, 14, "999 (påhittat betyg)");

        assertThatThrownBy(() -> parser.parse(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eget betyg");
    }

    private Row minimalRad() {
        Row row = radMed(sheet -> {
        });
        skrivCell(row, 0, "Vitt");
        skrivCell(row, 1, "Frankrike");
        skrivCell(row, 5, "Joseph Drouhin");
        skrivCell(row, 6, "Saint-Véran");
        return row;
    }

    private Row radMed(java.util.function.Consumer<Sheet> förberedelse) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Vin");
        förberedelse.accept(sheet);
        return sheet.createRow(1);
    }

    private void skrivCell(Row row, int col, String värde) {
        row.createCell(col).setCellValue(värde);
    }

    private void skrivCell(Row row, int col, double värde) {
        row.createCell(col).setCellValue(värde);
    }
}
