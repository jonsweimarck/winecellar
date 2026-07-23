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
 * Skriver ett Wine till en rad - den omvända operationen av WineRowParser,
 * med samma kolumnlayout (WineRowParser.COL_*, delade paketsynliga
 * konstanter så de två klasserna aldrig kan glida isär).
 *
 * Bild-kolumnen (I, WineRowParser.COL_IMAGE) skriver etiketten som en
 * vanlig ankrad POI-{@code Picture} (byggt 2026-07-22, på användarens
 * begäran - "kan vi också exportera bilderna?") - **inte** samma sak som
 * Excelkällfilens ursprungliga "bild i cell" (inbäddad rich data), som
 * WineRowParser fortfarande medvetet inte läser vid import (se dess
 * klasskommentar). En vanlig ankrad bild är en helt annan, mycket
 * enklare mekanism att skriva än att läsa rich-data-celler.
 *
 * Den ankrade bilden i sig läses INTE tillbaka av ImportExcel - det är
 * bara en visuell bekvämlighet för att kunna bläddra i xlsx-filen. Den
 * fullständiga rundtrippen (byggd samma dag, se ExportExcels
 * klasskommentar) går istället via WINECELLAR_LOCAL_IMAGE_FOLDER: samma
 * bilddata skrivs ALLTID som en riktig fil i den mappen (oavsett om
 * formatet stöds av xlsx-ankring eller inte), och det är den mappen
 * ImportExcel/ImageMatcher läser tillbaka från vid en återimport.
 */
final class WineRowWriter {

    private static final Map<WineType, String> SWEDISH_WINE_TYPE = Map.of(
            WineType.RED, "Rött",
            WineType.WHITE, "Vitt",
            WineType.ROSE, "Rosé",
            WineType.SPARKLING, "Mousserande",
            WineType.FORTIFIED, "Starkvin"
    );

    // Samma MIME-typer som ImageMatcher känner igen vid import, MINUS
    // "image/webp" - OOXML (.xlsx) har inget bildformat för webp, och
    // POI har ingen PICTURE_TYPE-konstant för det. En sådan bild hoppas
    // över vid export med en utskriven varning istället för att krascha.
    private static final Map<String, Integer> POI_PICTURE_TYPE_BY_MIME = Map.of(
            "image/jpeg", Workbook.PICTURE_TYPE_JPEG,
            "image/png", Workbook.PICTURE_TYPE_PNG,
            "image/gif", XSSFWorkbook.PICTURE_TYPE_GIF
    );

    /**
     * `dateFormat` skapas en gång av anroparen (ExportExcel) och
     * återanvänds för alla rader - en ny CellStyle per cell är ett känt
     * POI-antimönster (workbookets stilpool är begränsad). `drawing`
     * (sheet.createDrawingPatriarch()) skapas också en gång och delas -
     * en `Drawing` är sidans enda "canvas" för ankrade figurer, inte en
     * per-rad-resurs.
     */
    void write(Wine wine, Row row, CellStyle dateFormat, Drawing<?> drawing) {
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
        image(row, wine, drawing);
    }

    private void image(Row row, Wine wine, Drawing<?> drawing) {
        if (wine.image() == null) {
            return;
        }
        Integer poiPictureType = POI_PICTURE_TYPE_BY_MIME.get(wine.imageMimeType());
        if (poiPictureType == null) {
            System.out.println("Varning: bilden för \"" + wine.name() + "\" har MIME-typen \""
                    + wine.imageMimeType() + "\", som Excel-export inte stöder - bilden hoppas över "
                    + "(databasens rådata påverkas inte).");
            return;
        }
        int pictureIndex = row.getSheet().getWorkbook().addPicture(wine.image(), poiPictureType);
        ClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                WineRowParser.COL_IMAGE, row.getRowNum(), WineRowParser.COL_IMAGE + 1, row.getRowNum() + 1);
        drawing.createPicture(anchor, pictureIndex);
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
