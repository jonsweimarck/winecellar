package com.example.winecellar.importexcel;

import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mappar en rad från Vin-fliken i Vinlista.xlsx till ett Wine. Kolumn-
 * layouten (A-V) är fast - se README:s Datamodell-avsnitt för vilket
 * Wine-fält varje kolumn motsvarar. Bild-kolumnen (I) hoppas medvetet
 * över: bilderna är inbäddade som Excels "bild i cell" (rich data), inte
 * vanliga cellvärden, och att extrahera dem robust är inte värt det för
 * ett engångsskript. Etiketter importeras istället separat från en
 * vanlig bildmapp - se {@link Bildmatchare} och ImportExcel - eller laddas
 * upp manuellt via webb-UI:t (vin-formuläret) efteråt.
 *
 * **Bara namnet är obligatoriskt (ändrat 2026-07-22)** - samma regel som
 * webb-UI:t använder (se CLAUDE.md:s "Bara namnet obligatoriskt"). Alla
 * övriga fält, inklusive vintyp/land/producent som tidigare krävdes,
 * tolkas nu som valfria och blir `null` om cellen är tom.
 */
final class VinradParser {

    // Paketsynliga (inte private) sedan ExportExcel/VinradSkrivare
    // tillkom 2026-07-22 - samma kolumnlayout måste hållas i synk åt
    // båda hållen, en delad källa till sanning är säkrare än att
    // duplicera indexen i två klasser.
    static final int COL_VINTYP = 0;
    static final int COL_LAND = 1;
    static final int COL_REGION = 2;
    static final int COL_UNDERREGION = 3;
    static final int COL_DRUVOR = 4;
    static final int COL_PRODUCENT = 5;
    static final int COL_NAMN = 6;
    static final int COL_ARGANG = 7;
    // Hoppas över vid IMPORT (se klasskommentar) - men används av
    // VinradSkrivare/ExportExcel för att ankra exporterade bilder i rätt
    // kolumn (byggt 2026-07-22, se CLAUDE.md).
    static final int COL_BILD = 8;
    static final int COL_INKOPSDATUM = 9;
    static final int COL_PRIS = 10;
    static final int COL_ANTAL = 11;
    static final int COL_VARFOR_KOP = 12;
    static final int COL_TASTING_NOTES = 13;
    static final int COL_EGET_BETYG = 14;
    // "Systembolagets prodnummer" - egen kolumn sedan 2026-07-20, tidigare
    // ihopklistrad med beskrivningen i COL_SYSTEMBOLAGET (se git-historiken).
    static final int COL_SYSTEMBOLAGET_PRODUKTNUMMER = 15;
    static final int COL_SYSTEMBOLAGET = 16;
    static final int COL_MUNSKANKARNA_BEDOMNING = 17;
    static final int COL_MUNSKANKARNA_BETYG = 18;
    static final int COL_VIVINO = 19;
    static final int COL_ANNAN_REFERENS = 20;
    static final int COL_VAR = 21;

    private static final Map<String, WineType> VINTYPER = Map.of(
            "rött", WineType.RED,
            "vitt", WineType.WHITE,
            "rosé", WineType.ROSE,
            "mousserande", WineType.SPARKLING,
            "starkvin", WineType.FORTIFIED
    );

    private static final Pattern LEDANDE_TAL = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)");

    private final DataFormatter dataFormatter = new DataFormatter(Locale.of("sv", "SE"));

    /**
     * Kastar {@link RadSaknarObligatoriskaFältException} istället för att
     * returnera null - anroparen (ImportExcel) avgör om det ska hoppas
     * över med en varning eller stoppa hela importen. Bara namnet krävs
     * (se klasskommentaren) - en rad utan namn kan inte bli ett vin över
     * huvud taget, men alla andra fält får gärna vara tomma.
     */
    Wine parse(Row row) {
        String namn = text(row, COL_NAMN);
        if (namn == null) {
            throw new RadSaknarObligatoriskaFältException(row.getRowNum() + 1);
        }
        String vintypText = text(row, COL_VINTYP);

        return Wine.builder()
                .wineType(vintypText == null ? null : vinTypFrån(vintypText, row.getRowNum() + 1))
                .country(text(row, COL_LAND))
                .region(text(row, COL_REGION))
                .subregion(text(row, COL_UNDERREGION))
                .grapes(text(row, COL_DRUVOR))
                .producer(text(row, COL_PRODUCENT))
                .name(namn)
                .vintage(heltal(row, COL_ARGANG))
                .purchaseDate(datum(row, COL_INKOPSDATUM))
                .price(pris(row, COL_PRIS))
                .quantity(heltal(row, COL_ANTAL))
                .purchaseReason(text(row, COL_VARFOR_KOP))
                .tastingNotes(text(row, COL_TASTING_NOTES))
                .ownRating(betyg(row, COL_EGET_BETYG, row.getRowNum() + 1, "eget betyg"))
                .systembolagetProductNumber(text(row, COL_SYSTEMBOLAGET_PRODUKTNUMMER))
                .systembolagetDescription(text(row, COL_SYSTEMBOLAGET))
                .munskankarnaReview(text(row, COL_MUNSKANKARNA_BEDOMNING))
                .munskankarnaRating(betyg(row, COL_MUNSKANKARNA_BETYG, row.getRowNum() + 1, "munskänkarna-betyg"))
                .vivinoRating(vivino(row))
                .otherReference(text(row, COL_ANNAN_REFERENS))
                .location(text(row, COL_VAR))
                .build();
    }

    private WineType vinTypFrån(String svenskTyp, int radnummer) {
        WineType typ = VINTYPER.get(svenskTyp.toLowerCase(Locale.of("sv", "SE")));
        if (typ == null) {
            throw new IllegalArgumentException("Rad " + radnummer + ": okänd vintyp \"" + svenskTyp + "\"");
        }
        return typ;
    }

    private Rating betyg(Row row, int col, int radnummer, String fältnamn) {
        String etikett = text(row, col);
        if (etikett == null) {
            return null;
        }
        try {
            return Rating.fraEtikett(etikett);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rad " + radnummer + ": " + fältnamn + " \"" + etikett + "\" matchar inget av de 29 kända betygen", e);
        }
    }

    private BigDecimal pris(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        // Prisceller är ibland text med extra anteckning, t.ex. "329\n(2021)" -
        // plocka ut det första talet och strunta i resten.
        String rått = dataFormatter.formatCellValue(cell).trim();
        if (rått.isEmpty()) {
            return null;
        }
        Matcher matcher = LEDANDE_TAL.matcher(rått);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Rad " + (row.getRowNum() + 1) + ": kunde inte tolka pris ur \"" + rått + "\"");
        }
        return new BigDecimal(matcher.group(1).replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal vivino(Row row) {
        Cell cell = row.getCell(COL_VIVINO);
        if (cell == null) {
            return null;
        }
        double värde = cell.getCellType() == CellType.NUMERIC
                ? cell.getNumericCellValue()
                : Double.parseDouble(dataFormatter.formatCellValue(cell).replace(',', '.'));
        return BigDecimal.valueOf(värde).setScale(1, RoundingMode.HALF_UP);
    }

    private LocalDate datum(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() != CellType.NUMERIC) {
            return null;
        }
        LocalDateTime dateTime = DateUtil.isCellDateFormatted(cell)
                ? cell.getLocalDateTimeCellValue()
                : DateUtil.getLocalDateTime(cell.getNumericCellValue());
        return dateTime.toLocalDate();
    }

    private Integer heltal(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) Math.round(cell.getNumericCellValue());
        }
        String text = dataFormatter.formatCellValue(cell).trim();
        return text.isEmpty() ? null : Integer.parseInt(text);
    }

    private String text(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        String värde = dataFormatter.formatCellValue(cell).trim();
        return värde.isEmpty() ? null : värde;
    }

    static final class RadSaknarObligatoriskaFältException extends RuntimeException {
        RadSaknarObligatoriskaFältException(int radnummer) {
            super("Rad " + radnummer + ": saknar namn - hoppas över");
        }
    }
}
