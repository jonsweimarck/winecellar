package com.example.winecellar.importexcel;

import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Skriver ett Wine till en rad - den omvända operationen av VinradParser,
 * med samma kolumnlayout (VinradParser.COL_*, delade paketsynliga
 * konstanter så de två klasserna aldrig kan glida isär).
 *
 * Bild-kolumnen (I, VinradParser.COL_BILD) skriver etiketten som en
 * vanlig ankrad POI-{@code Picture} (byggt 2026-07-22, på användarens
 * begäran - "kan vi också exportera bilderna?") - **inte** samma sak som
 * Excelkällfilens ursprungliga "bild i cell" (inbäddad rich data), som
 * VinradParser fortfarande medvetet inte läser vid import (se dess
 * klasskommentar). En vanlig ankrad bild är en helt annan, mycket
 * enklare mekanism att skriva än att läsa rich-data-celler - men
 * ImportExcel läser INTE denna bild tillbaka; att koppla bilder vid
 * återimport sker fortfarande bara via WINECELLAR_IMPORT_IMAGE_FOLDER/
 * Bildmatchare. Exportfilen är alltså en fullständig visuell backup,
 * men en re-import läser fortfarande om bilder separat.
 */
final class VinradSkrivare {

    private static final Map<WineType, String> SVENSK_VINTYP = Map.of(
            WineType.RED, "Rött",
            WineType.WHITE, "Vitt",
            WineType.ROSE, "Rosé",
            WineType.SPARKLING, "Mousserande",
            WineType.FORTIFIED, "Starkvin"
    );

    // Samma MIME-typer som Bildmatchare känner igen vid import, MINUS
    // "image/webp" - OOXML (.xlsx) har inget bildformat för webp, och
    // POI har ingen PICTURE_TYPE-konstant för det. En sådan bild hoppas
    // över vid export med en utskriven varning istället för att krascha.
    private static final Map<String, Integer> POI_BILDTYP_PER_MIME = Map.of(
            "image/jpeg", Workbook.PICTURE_TYPE_JPEG,
            "image/png", Workbook.PICTURE_TYPE_PNG,
            "image/gif", XSSFWorkbook.PICTURE_TYPE_GIF
    );

    /**
     * `datumformat` skapas en gång av anroparen (ExportExcel) och
     * återanvänds för alla rader - en ny CellStyle per cell är ett känt
     * POI-antimönster (workbookets stilpool är begränsad). `ritning`
     * (sheet.createDrawingPatriarch()) skapas också en gång och delas -
     * en `Drawing` är sidans enda "canvas" för ankrade figurer, inte en
     * per-rad-resurs.
     */
    void skriv(Wine vin, Row row, CellStyle datumformat, Drawing<?> ritning) {
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
        bild(row, vin, ritning);
    }

    private void bild(Row row, Wine vin, Drawing<?> ritning) {
        if (vin.image() == null) {
            return;
        }
        Integer poiBildtyp = POI_BILDTYP_PER_MIME.get(vin.imageMimeType());
        if (poiBildtyp == null) {
            System.out.println("Varning: bilden för \"" + vin.name() + "\" har MIME-typen \""
                    + vin.imageMimeType() + "\", som Excel-export inte stöder - bilden hoppas över "
                    + "(databasens rådata påverkas inte).");
            return;
        }
        int bildindex = row.getSheet().getWorkbook().addPicture(vin.image(), poiBildtyp);
        ClientAnchor ankare = ritning.createAnchor(0, 0, 0, 0,
                VinradParser.COL_BILD, row.getRowNum(), VinradParser.COL_BILD + 1, row.getRowNum() + 1);
        ritning.createPicture(ankare, bildindex);
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
