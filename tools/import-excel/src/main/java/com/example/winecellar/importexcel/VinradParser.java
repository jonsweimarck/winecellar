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
 * layouten (A-U) är fast - se README:s Datamodell-avsnitt för vilket
 * Wine-fält varje kolumn motsvarar. Bild-kolumnen (I) hoppas medvetet
 * över: bilderna är inbäddade som Excels "bild i cell" (rich data), inte
 * vanliga cellvärden, och att extrahera dem robust är inte värt det för
 * ett engångsskript - ladda upp dem manuellt via webb-UI:t efteråt.
 */
final class VinradParser {

    private static final int COL_VINTYP = 0;
    private static final int COL_LAND = 1;
    private static final int COL_REGION = 2;
    private static final int COL_UNDERREGION = 3;
    private static final int COL_DRUVOR = 4;
    private static final int COL_PRODUCENT = 5;
    private static final int COL_NAMN = 6;
    private static final int COL_ARGANG = 7;
    // COL_BILD = 8, hoppas över, se klasskommentar.
    private static final int COL_INKOPSDATUM = 9;
    private static final int COL_PRIS = 10;
    private static final int COL_ANTAL = 11;
    private static final int COL_VARFOR_KOP = 12;
    private static final int COL_TASTING_NOTES = 13;
    private static final int COL_EGET_BETYG = 14;
    private static final int COL_SYSTEMBOLAGET = 15;
    private static final int COL_MUNSKANKARNA_BEDOMNING = 16;
    private static final int COL_MUNSKANKARNA_BETYG = 17;
    private static final int COL_VIVINO = 18;
    private static final int COL_ANNAN_REFERENS = 19;
    private static final int COL_VAR = 20;

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
     * över med en varning eller stoppa hela importen. Rader saknar
     * ibland vintyp/land/producent/namn (ofullständiga utkast i kalkylen,
     * se t.ex. rad 2 - inköpsdatum/pris/antal/plats saknas helt).
     */
    Wine parse(Row row) {
        String vintypText = text(row, COL_VINTYP);
        String land = text(row, COL_LAND);
        String producent = text(row, COL_PRODUCENT);
        String namn = text(row, COL_NAMN);
        if (vintypText == null || land == null || producent == null || namn == null) {
            throw new RadSaknarObligatoriskaFältException(row.getRowNum() + 1);
        }

        return Wine.builder()
                .wineType(vinTypFrån(vintypText, row.getRowNum() + 1))
                .country(land)
                .region(text(row, COL_REGION))
                .subregion(text(row, COL_UNDERREGION))
                .grapes(text(row, COL_DRUVOR))
                .producer(producent)
                .name(namn)
                .vintage(heltal(row, COL_ARGANG, 0))
                .purchaseDate(datum(row, COL_INKOPSDATUM))
                .price(pris(row, COL_PRIS))
                .quantity(heltal(row, COL_ANTAL, 0))
                .purchaseReason(text(row, COL_VARFOR_KOP))
                .tastingNotes(text(row, COL_TASTING_NOTES))
                .ownRating(betyg(row, COL_EGET_BETYG, row.getRowNum() + 1, "eget betyg"))
                .systembolagetProductNumber(systembolagetProduktnummer(row))
                .systembolagetDescription(systembolagetBeskrivning(row))
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

    private String systembolagetProduktnummer(Row row) {
        String rått = text(row, COL_SYSTEMBOLAGET);
        if (rått == null) {
            return null;
        }
        int nyradIndex = rått.indexOf('\n');
        return (nyradIndex == -1 ? rått : rått.substring(0, nyradIndex)).trim();
    }

    private String systembolagetBeskrivning(Row row) {
        String rått = text(row, COL_SYSTEMBOLAGET);
        if (rått == null) {
            return null;
        }
        int nyradIndex = rått.indexOf('\n');
        if (nyradIndex == -1) {
            return null;
        }
        String beskrivning = rått.substring(nyradIndex + 1).trim();
        return beskrivning.isEmpty() ? null : beskrivning;
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

    private int heltal(Row row, int col, int standardvärde) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return standardvärde;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) Math.round(cell.getNumericCellValue());
        }
        String text = dataFormatter.formatCellValue(cell).trim();
        return text.isEmpty() ? standardvärde : Integer.parseInt(text);
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
            super("Rad " + radnummer + ": saknar vintyp, land, producent eller namn - hoppas över");
        }
    }
}
