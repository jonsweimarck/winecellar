package com.example.winecellar.importexcel;

import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Skriver ett Wine till en rad - den omvända operationen av VinradParser,
 * med samma kolumnlayout (VinradParser.COL_*, delade paketsynliga
 * konstanter så de två klasserna aldrig kan glida isär). Bild-kolumnen
 * (I) hoppas medvetet över, av samma skäl som på importsidan (se
 * VinradParser:s klasskommentar) - etiketter exporteras inte till
 * Excel-celler, bara den vanliga celldatan.
 */
final class VinradSkrivare {

    private static final Map<WineType, String> SVENSK_VINTYP = Map.of(
            WineType.RED, "Rött",
            WineType.WHITE, "Vitt",
            WineType.ROSE, "Rosé",
            WineType.SPARKLING, "Mousserande",
            WineType.FORTIFIED, "Starkvin"
    );

    /**
     * `datumformat` skapas en gång av anroparen (ExportExcel) och
     * återanvänds för alla rader - en ny CellStyle per cell är ett känt
     * POI-antimönster (workbookets stilpool är begränsad).
     */
    void skriv(Wine vin, Row row, CellStyle datumformat) {
        text(row, VinradParser.COL_VINTYP, vin.wineType() == null ? null : SVENSK_VINTYP.get(vin.wineType()));
        text(row, VinradParser.COL_LAND, vin.country());
        text(row, VinradParser.COL_REGION, vin.region());
        text(row, VinradParser.COL_UNDERREGION, vin.subregion());
        text(row, VinradParser.COL_DRUVOR, vin.grapes());
        text(row, VinradParser.COL_PRODUCENT, vin.producer());
        text(row, VinradParser.COL_NAMN, vin.name());
        heltal(row, VinradParser.COL_ARGANG, vin.vintage());
        datum(row, VinradParser.COL_INKOPSDATUM, vin.purchaseDate(), datumformat);
        decimal(row, VinradParser.COL_PRIS, vin.price());
        heltal(row, VinradParser.COL_ANTAL, vin.quantity());
        text(row, VinradParser.COL_VARFOR_KOP, vin.purchaseReason());
        text(row, VinradParser.COL_TASTING_NOTES, vin.tastingNotes());
        text(row, VinradParser.COL_EGET_BETYG, vin.ownRating() == null ? null : vin.ownRating().label());
        text(row, VinradParser.COL_SYSTEMBOLAGET_PRODUKTNUMMER, vin.systembolagetProductNumber());
        text(row, VinradParser.COL_SYSTEMBOLAGET, vin.systembolagetDescription());
        text(row, VinradParser.COL_MUNSKANKARNA_BEDOMNING, vin.munskankarnaReview());
        text(row, VinradParser.COL_MUNSKANKARNA_BETYG, vin.munskankarnaRating() == null ? null : vin.munskankarnaRating().label());
        decimal(row, VinradParser.COL_VIVINO, vin.vivinoRating());
        text(row, VinradParser.COL_ANNAN_REFERENS, vin.otherReference());
        text(row, VinradParser.COL_VAR, vin.location());
    }

    private void text(Row row, int col, String värde) {
        if (värde != null) {
            row.createCell(col).setCellValue(värde);
        }
    }

    private void heltal(Row row, int col, Integer värde) {
        if (värde != null) {
            row.createCell(col).setCellValue(värde.doubleValue());
        }
    }

    private void decimal(Row row, int col, BigDecimal värde) {
        if (värde != null) {
            row.createCell(col).setCellValue(värde.doubleValue());
        }
    }

    private void datum(Row row, int col, LocalDate värde, CellStyle datumformat) {
        if (värde != null) {
            Cell cell = row.createCell(col);
            cell.setCellValue(värde);
            cell.setCellStyle(datumformat);
        }
    }
}
