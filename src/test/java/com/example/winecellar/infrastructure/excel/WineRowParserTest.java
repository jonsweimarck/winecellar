package com.example.winecellar.infrastructure.excel;

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
class WineRowParserTest {

    private final WineRowParser parser = new WineRowParser();

    @Test
    void skaMappaEnFullständigRadKorrekt() {
        Row row = rowWith(sheet -> {
        });
        writeCell(row, 0, "Rött");
        writeCell(row, 1, "Italien");
        writeCell(row, 2, "Sicilien");
        writeCell(row, 3, "Terre Siciliane");
        writeCell(row, 4, "85% nerello mascalese, 15% övriga druvor");
        writeCell(row, 5, "Eduardo Torres Acosta");
        writeCell(row, 6, "Versante Nord");
        writeCell(row, 7, 2022);
        writeCell(row, 8, DateUtil.getExcelDate(LocalDate.of(2024, 3, 15)));
        writeCell(row, 9, 260);
        writeCell(row, 10, 3);
        writeCell(row, 11, "Favorit, \"Present, jul\"");
        writeCell(row, 12, "Prisvärt enligt munskänkarna");
        writeCell(row, 13, "Ljusröd, doft av jordgubbe.");
        writeCell(row, 14, "16 (15 - 17,5 Högklassigt vin)");
        writeCell(row, 15, "9363301");
        writeCell(row, 16, "Nyanserad, kryddig smak.");
        writeCell(row, 17, "Mer än prisvärt\n\nNågot återhållen doft.");
        writeCell(row, 18, "14,5 (12 - 14,5 Bra till mycket bra vin)");
        writeCell(row, 19, 4.0999999999999996);
        writeCell(row, 20, "https://example.com/vin");
        writeCell(row, 21, "Låda 2");

        Wine wine = parser.parse(row);

        assertThat(wine.wineType()).isEqualTo(WineType.RED);
        assertThat(wine.country()).isEqualTo("Italien");
        assertThat(wine.region()).isEqualTo("Sicilien");
        assertThat(wine.subregion()).isEqualTo("Terre Siciliane");
        assertThat(wine.grapes()).isEqualTo("85% nerello mascalese, 15% övriga druvor");
        assertThat(wine.producer()).isEqualTo("Eduardo Torres Acosta");
        assertThat(wine.name()).isEqualTo("Versante Nord");
        assertThat(wine.vintage()).isEqualTo(2022);
        assertThat(wine.purchaseDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(wine.price()).isEqualByComparingTo("260.00");
        assertThat(wine.quantity()).isEqualTo(3);
        assertThat(wine.tags()).containsExactlyInAnyOrder("Favorit", "Present, jul");
        assertThat(wine.purchaseReason()).isEqualTo("Prisvärt enligt munskänkarna");
        assertThat(wine.tastingNotes()).isEqualTo("Ljusröd, doft av jordgubbe.");
        assertThat(wine.ownRating()).isEqualTo("16 (15 - 17,5 Högklassigt vin)");
        assertThat(wine.systembolagetProductNumber()).isEqualTo("9363301");
        assertThat(wine.systembolagetDescription()).isEqualTo("Nyanserad, kryddig smak.");
        assertThat(wine.munskankarnaReview()).isEqualTo("Mer än prisvärt\n\nNågot återhållen doft.");
        assertThat(wine.munskankarnaRating()).isEqualTo(Rating.R14_5);
        assertThat(wine.vivinoRating()).isEqualByComparingTo("4.1");
        assertThat(wine.otherReference()).isEqualTo("https://example.com/vin");
        assertThat(wine.location()).isEqualTo("Låda 2");
    }

    @Test
    void skaHoppaÖverRadSomSaknarNamn() {
        Row row = rowWith(sheet -> {
        });
        writeCell(row, 0, "Rött");
        writeCell(row, 1, "Frankrike");
        writeCell(row, 5, "Domaine Fond Moiroux");
        writeCell(row, 10, 3);
        // Ingen namn-cell (kolumn 6) - motsvarar en ofullständig utkastrad i kalkylen.

        assertThatThrownBy(() -> parser.parse(row))
                .isInstanceOf(WineRowParser.RowMissingRequiredFieldsException.class)
                .hasMessage("Rad 2: Vinet måste ha ett namn");
    }

    @Test
    void skaHoppaÖverRadSomSaknarAntal() {
        Row row = rowWith(sheet -> {
        });
        writeCell(row, 6, "Anteckning om ett vin");
        // Ingen antal-cell (kolumn 10) - antal är obligatoriskt sedan ADR 0016,
        // precis som namnet.

        assertThatThrownBy(() -> parser.parse(row))
                .isInstanceOf(WineRowParser.RowMissingRequiredFieldsException.class)
                .hasMessage("Rad 2: Vinet måste ha antal flaskor angivet");
    }

    @Test
    void skaHoppaÖverRadSomSaknarBådeNamnOchAntal() {
        Row row = rowWith(sheet -> {
        });
        writeCell(row, 0, "Rött");
        writeCell(row, 1, "Frankrike");
        writeCell(row, 5, "Domaine Fond Moiroux");
        // Varken namn-cell (kolumn 6) eller antal-cell (kolumn 10) är ifylld.

        assertThatThrownBy(() -> parser.parse(row))
                .isInstanceOf(WineRowParser.RowMissingRequiredFieldsException.class)
                .hasMessage("Rad 2: Vinet måste ha ett namn och antal flaskor angivet");
    }

    @Test
    void skaKastaTydligtFelOmÅrgångInteKanTolkasSomTal() {
        Row row = minimalRow();
        writeCell(row, 7, "okänt");

        assertThatThrownBy(() -> parser.parse(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rad 2: kunde inte tolka årgång ur \"okänt\"");
    }

    @Test
    void skaKastaTydligtFelOmAntalInteKanTolkasSomTal() {
        Row row = rowWith(sheet -> {
        });
        writeCell(row, 6, "Ett vin");
        writeCell(row, 10, "flera");

        assertThatThrownBy(() -> parser.parse(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rad 2: kunde inte tolka antal flaskor ur \"flera\"");
    }

    @Test
    void skaTillåtaRadMedBaraNamnOchAntalIfyllt() {
        Row row = rowWith(sheet -> {
        });
        writeCell(row, 6, "Anteckning om ett vin");
        writeCell(row, 10, 1);
        // Alla andra kolumner lämnas tomma - namn och antal är de enda
        // obligatoriska fälten, samma regel som webb-UI:t (se ADR 0016).

        Wine wine = parser.parse(row);

        assertThat(wine.name()).isEqualTo("Anteckning om ett vin");
        assertThat(wine.wineType()).isNull();
        assertThat(wine.country()).isNull();
        assertThat(wine.producer()).isNull();
        assertThat(wine.vintage()).isNull();
        assertThat(wine.quantity()).isEqualTo(1);
        assertThat(wine.tags()).isEmpty();
    }

    /**
     * WINE-51: taggkolumnen (L) är en kommaseparerad lista - en tagg som
     * själv innehåller ett kommatecken citeras (standard CSV-citering).
     * Den faktiska parsnings-/skrivningslogiken (inkl. citattecken-i-
     * citattecken) testas i sin helhet i {@link TagListCsvTest} - det här
     * testet verifierar bara att WineRowParser faktiskt kopplar rätt
     * kolumn till {@link TagListCsv#parse(String)}.
     */
    @Test
    void skaLäsaTaggarSomKommasepararadListaMedCitattecknadTaggInnehållandeKommatecken() {
        Row row = minimalRow();
        writeCell(row, 11, "Favorit, \"Fest, jul\"");

        Wine wine = parser.parse(row);

        assertThat(wine.tags()).containsExactlyInAnyOrder("Favorit", "Fest, jul");
    }

    /**
     * WINE-50: "Munskänkarnas betyg" (kolumn 17, till skillnad från "Eget
     * betyg" i kolumn 13) är oförändrat en sluten betygsskala som
     * fortfarande normaliserar mellanslag - det här testet flyttades hit
     * från kolumn 13, som inte längre validerar/normaliserar något (se
     * skaLäsaEgetBetygSomFriTextUtanValidering nedan).
     */
    @Test
    void skaMatchaMunskänkarnasBetygMedDubblaMellanslagIKällfilen() {
        Row row = minimalRow();
        // Källfilens rad för 8,5 har dubbla mellanslag: "8,5  (6 - 8,5  Enkel vin)".
        writeCell(row, 18, "8,5  (6 - 8,5  Enkel vin)");

        Wine wine = parser.parse(row);

        assertThat(wine.munskankarnaRating()).isEqualTo(Rating.R8_5);
    }

    /**
     * WINE-50: "Eget betyg" är fri text sedan denna story - till skillnad
     * från "Munskänkarnas betyg" (se ovan/nedan) läses kolumnen rakt av,
     * utan normalisering eller validering mot de 29 kända betygen.
     */
    @Test
    void skaLäsaEgetBetygSomFriTextUtanValidering() {
        Row row = minimalRow();
        writeCell(row, 14, "999 (påhittat betyg, inte alls i skalan)");

        Wine wine = parser.parse(row);

        assertThat(wine.ownRating()).isEqualTo("999 (påhittat betyg, inte alls i skalan)");
    }

    @Test
    void skaTolkaPrisMedExtraAnteckningPåEgenRad() {
        Row row = minimalRow();
        writeCell(row, 9, "329\n(2021)");

        Wine wine = parser.parse(row);

        assertThat(wine.price()).isEqualByComparingTo("329.00");
    }

    @Test
    void skaLämnaSystembolagetsBeskrivningNullOmDenSaknasMenAnvändaProduktnummerkolumnen() {
        Row row = minimalRow();
        writeCell(row, 15, "5020201");
        // Kolumn 16 (beskrivningen) lämnas tom.

        Wine wine = parser.parse(row);

        assertThat(wine.systembolagetProductNumber()).isEqualTo("5020201");
        assertThat(wine.systembolagetDescription()).isNull();
    }

    @Test
    void skaKastaTydligtFelOmMunskänkarnasBetygInteMatcharNågotAvDe29Kända() {
        Row row = minimalRow();
        writeCell(row, 18, "999 (påhittat betyg)");

        assertThatThrownBy(() -> parser.parse(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("munskänkarna-betyg");
    }

    private Row minimalRow() {
        Row row = rowWith(sheet -> {
        });
        writeCell(row, 0, "Vitt");
        writeCell(row, 1, "Frankrike");
        writeCell(row, 5, "Joseph Drouhin");
        writeCell(row, 6, "Saint-Véran");
        writeCell(row, 10, 1);
        return row;
    }

    private Row rowWith(java.util.function.Consumer<Sheet> preparation) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Vin");
        preparation.accept(sheet);
        return sheet.createRow(1);
    }

    private void writeCell(Row row, int col, String value) {
        row.createCell(col).setCellValue(value);
    }

    private void writeCell(Row row, int col, double value) {
        row.createCell(col).setCellValue(value);
    }
}
