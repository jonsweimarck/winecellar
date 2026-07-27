package com.example.winecellar.infrastructure.excel;

import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Skriver ett Wine till en rad - den omvända operationen av WineRowParser,
 * med samma kolumnlayout (WineRowParser.COL_*, delade paketsynliga
 * konstanter så de två klasserna aldrig kan glida isär).
 *
 * **Ingen egen bild-kolumn** (WINE-32, 2026-07-27) - skrev tidigare
 * etiketten som en ankrad POI-{@code Picture} i en egen kolumn, men det
 * var bara en visuell bekvämlighet som aldrig lästes tillbaka av
 * WineRowParser. Riktig bildrundtripp går via en separat mekanism (se
 * ADR 0014/WINE-23, `/export/bilder.zip`). Se
 * `docs/adr/0011-excel-image-roundtrip-dual-mechanism.md` (Deprecated).
 *
 * `SHEET_NAME`/{@link #writeHeaderRow} tillkom i WINE-22 (webbaserad
 * export) - samma flikamn/rubrikrad som den tidigare CLI-exporten
 * (`ExportExcel`, borttagen i WINE-20) skrev, flyttade hit eftersom de
 * hör till samma delade kolumnlayout som resten av klassen.
 */
public final class WineRowWriter {

    public static final String SHEET_NAME = "Vin";

    private static final String[] HEADERS = {
            "Vintyp", "Land", "Region", "Underregion", "Druvor", "Producent", "Namn", "Årgång",
            "Inköpsdatum", "Pris", "Antal", "Varför köpt", "Tasting notes", "Eget betyg",
            "Systembolagets prodnummer", "Systembolagets beskrivning", "Munskänkarnas bedömning",
            "Munskänkarnas betyg", "Vivino", "Annan referens", "Plats"
    };

    private static final Map<WineType, String> SWEDISH_WINE_TYPE = Map.of(
            WineType.RED, "Rött",
            WineType.WHITE, "Vitt",
            WineType.ROSE, "Rosé",
            WineType.SPARKLING, "Mousserande",
            WineType.FORTIFIED, "Starkvin"
    );

    /**
     * `dateFormat` skapas en gång av anroparen och återanvänds för alla
     * rader - en ny CellStyle per cell är ett känt POI-antimönster
     * (workbookets stilpool är begränsad).
     */
    public void write(Wine wine, Row row, CellStyle dateFormat) {
        text(row, WineRowParser.COL_WINE_TYPE, wine.wineType() == null ? null : SWEDISH_WINE_TYPE.get(wine.wineType()));
        text(row, WineRowParser.COL_COUNTRY, wine.country());
        text(row, WineRowParser.COL_REGION, wine.region());
        text(row, WineRowParser.COL_SUBREGION, wine.subregion());
        text(row, WineRowParser.COL_GRAPES, wine.grapes());
        text(row, WineRowParser.COL_PRODUCER, wine.producer());
        text(row, WineRowParser.COL_NAME, wine.name());
        integer(row, WineRowParser.COL_VINTAGE, wine.vintage());
        date(row, WineRowParser.COL_PURCHASE_DATE, wine.purchaseDate(), dateFormat);
        decimal(row, WineRowParser.COL_PRICE, wine.price());
        integer(row, WineRowParser.COL_QUANTITY, wine.quantity());
        text(row, WineRowParser.COL_PURCHASE_REASON, wine.purchaseReason());
        text(row, WineRowParser.COL_TASTING_NOTES, wine.tastingNotes());
        text(row, WineRowParser.COL_OWN_RATING, wine.ownRating() == null ? null : wine.ownRating().label());
        text(row, WineRowParser.COL_SYSTEMBOLAGET_PRODUCT_NUMBER, wine.systembolagetProductNumber());
        text(row, WineRowParser.COL_SYSTEMBOLAGET, wine.systembolagetDescription());
        text(row, WineRowParser.COL_MUNSKANKARNA_REVIEW, wine.munskankarnaReview());
        text(row, WineRowParser.COL_MUNSKANKARNA_RATING, wine.munskankarnaRating() == null ? null : wine.munskankarnaRating().label());
        decimal(row, WineRowParser.COL_VIVINO, wine.vivinoRating());
        text(row, WineRowParser.COL_OTHER_REFERENCE, wine.otherReference());
        text(row, WineRowParser.COL_LOCATION, wine.location());
    }

    /**
     * Skriver rubrikraden (rad 0) - anroparen ansvarar för att sedan
     * börja skriva vinrader från rad 1 (samma konvention som
     * `WineRowParser`s `row 0 == rubrikrad, hoppas över vid tolkning`).
     */
    public static void writeHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        for (int column = 0; column < HEADERS.length; column++) {
            headerRow.createCell(column).setCellValue(HEADERS[column]);
        }
    }

    private void text(Row row, int col, String value) {
        if (value != null) {
            row.createCell(col).setCellValue(value);
        }
    }

    private void integer(Row row, int col, Integer value) {
        if (value != null) {
            row.createCell(col).setCellValue(value.doubleValue());
        }
    }

    private void decimal(Row row, int col, BigDecimal value) {
        if (value != null) {
            row.createCell(col).setCellValue(value.doubleValue());
        }
    }

    private void date(Row row, int col, LocalDate value, CellStyle dateFormat) {
        if (value != null) {
            Cell cell = row.createCell(col);
            cell.setCellValue(value);
            cell.setCellStyle(dateFormat);
        }
    }
}
