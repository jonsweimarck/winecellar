package com.example.winecellar.infrastructure.excel;

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
 * Wine-fält varje kolumn motsvarar. Etiketter importeras separat - se
 * {@link ImageMatcher}.
 *
 * **Ingen egen bild-kolumn** (WINE-32, 2026-07-27) - källfilens
 * ursprungliga "bild i cell" (Excels inbäddade rich data) lästes redan
 * aldrig här (att extrahera den robust var aldrig värt komplexiteten),
 * och den ankrade POI-bilden som `WineRowWriter` en gång skrev till
 * samma kolumn vid export var bara en visuell bekvämlighet - ingen av
 * de två användes någonsin av en efterföljande import. Se
 * `docs/adr/0011-excel-image-roundtrip-dual-mechanism.md` (Deprecated).
 *
 * Flyttad in i huvudappen från den tidigare fristående `tools/import-excel`-
 * modulen (WINE-20, se ADR 0014) - motsvarande CLI-verktyg
 * (`ImportExcel`/`ExportExcel`, `DatabaseConnection`) är borttagna, den här
 * klassen och dess syskon (`WineRowWriter`, `ImageMatcher`) lever vidare som
 * återanvändbar kod åt den kommande webbaserade import-/exportfunktionen.
 *
 * **Namn och antal flaskor är obligatoriska** (se ADR 0016) - samma regel
 * som webb-UI:t använder. Alla övriga fält, inklusive vintyp/land/producent
 * som tidigare krävdes, tolkas som valfria och blir `null` om cellen är tom.
 */
public final class WineRowParser {

    // Paketsynliga (inte private) sedan ExportExcel/WineRowWriter
    // tillkom 2026-07-22 - samma kolumnlayout måste hållas i synk åt
    // båda hållen, en delad källa till sanning är säkrare än att
    // duplicera indexen i två klasser.
    static final int COL_WINE_TYPE = 0;
    static final int COL_COUNTRY = 1;
    static final int COL_REGION = 2;
    static final int COL_SUBREGION = 3;
    static final int COL_GRAPES = 4;
    static final int COL_PRODUCER = 5;
    static final int COL_NAME = 6;
    static final int COL_VINTAGE = 7;
    static final int COL_PURCHASE_DATE = 8;
    static final int COL_PRICE = 9;
    static final int COL_QUANTITY = 10;
    static final int COL_PURCHASE_REASON = 11;
    static final int COL_TASTING_NOTES = 12;
    static final int COL_OWN_RATING = 13;
    // "Systembolagets prodnummer" - egen kolumn sedan 2026-07-20, tidigare
    // ihopklistrad med beskrivningen i COL_SYSTEMBOLAGET (se git-historiken).
    static final int COL_SYSTEMBOLAGET_PRODUCT_NUMBER = 14;
    static final int COL_SYSTEMBOLAGET = 15;
    static final int COL_MUNSKANKARNA_REVIEW = 16;
    static final int COL_MUNSKANKARNA_RATING = 17;
    static final int COL_VIVINO = 18;
    static final int COL_OTHER_REFERENCE = 19;
    static final int COL_LOCATION = 20;

    private static final Map<String, WineType> WINE_TYPES = Map.of(
            "rött", WineType.RED,
            "vitt", WineType.WHITE,
            "rosé", WineType.ROSE,
            "mousserande", WineType.SPARKLING,
            "starkvin", WineType.FORTIFIED
    );

    private static final Pattern LEADING_NUMBER = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)");

    private final DataFormatter dataFormatter = new DataFormatter(Locale.of("sv", "SE"));

    /**
     * Kastar {@link RowMissingRequiredFieldsException} istället för att
     * returnera null - anroparen avgör om det ska hoppas över med en
     * varning eller stoppa hela importen. Namn OCH antal flaskor krävs
     * (se klasskommentaren/ADR 0016) - en rad utan endera kan inte bli
     * ett vin över huvud taget, men alla andra fält får gärna vara tomma.
     */
    public Wine parse(Row row) {
        String name = text(row, COL_NAME);
        Integer quantity = integer(row, COL_QUANTITY, "antal flaskor");
        if (name == null || quantity == null) {
            throw new RowMissingRequiredFieldsException(row.getRowNum() + 1, name == null, quantity == null);
        }
        String wineTypeText = text(row, COL_WINE_TYPE);

        return Wine.builder()
                .wineType(wineTypeText == null ? null : wineTypeFromSwedish(wineTypeText, row.getRowNum() + 1))
                .country(text(row, COL_COUNTRY))
                .region(text(row, COL_REGION))
                .subregion(text(row, COL_SUBREGION))
                .grapes(text(row, COL_GRAPES))
                .producer(text(row, COL_PRODUCER))
                .name(name)
                .vintage(integer(row, COL_VINTAGE, "årgång"))
                .purchaseDate(date(row, COL_PURCHASE_DATE))
                .price(price(row, COL_PRICE))
                .quantity(quantity)
                .purchaseReason(text(row, COL_PURCHASE_REASON))
                .tastingNotes(text(row, COL_TASTING_NOTES))
                .ownRating(rating(row, COL_OWN_RATING, row.getRowNum() + 1, "eget betyg"))
                .systembolagetProductNumber(text(row, COL_SYSTEMBOLAGET_PRODUCT_NUMBER))
                .systembolagetDescription(text(row, COL_SYSTEMBOLAGET))
                .munskankarnaReview(text(row, COL_MUNSKANKARNA_REVIEW))
                .munskankarnaRating(rating(row, COL_MUNSKANKARNA_RATING, row.getRowNum() + 1, "munskänkarna-betyg"))
                .vivinoRating(vivino(row))
                .otherReference(text(row, COL_OTHER_REFERENCE))
                .location(text(row, COL_LOCATION))
                .build();
    }

    private WineType wineTypeFromSwedish(String swedishType, int rowNumber) {
        WineType type = WINE_TYPES.get(swedishType.toLowerCase(Locale.of("sv", "SE")));
        if (type == null) {
            throw new IllegalArgumentException("Rad " + rowNumber + ": okänd vintyp \"" + swedishType + "\"");
        }
        return type;
    }

    private Rating rating(Row row, int col, int rowNumber, String fieldName) {
        String label = text(row, col);
        if (label == null) {
            return null;
        }
        try {
            return Rating.fromLabel(label);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rad " + rowNumber + ": " + fieldName + " \"" + label + "\" matchar inget av de 29 kända betygen", e);
        }
    }

    private BigDecimal price(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        // Prisceller är ibland text med extra anteckning, t.ex. "329\n(2021)" -
        // plocka ut det första talet och strunta i resten.
        String raw = dataFormatter.formatCellValue(cell).trim();
        if (raw.isEmpty()) {
            return null;
        }
        Matcher matcher = LEADING_NUMBER.matcher(raw);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Rad " + (row.getRowNum() + 1) + ": kunde inte tolka pris ur \"" + raw + "\"");
        }
        return new BigDecimal(matcher.group(1).replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal vivino(Row row) {
        Cell cell = row.getCell(COL_VIVINO);
        if (cell == null) {
            return null;
        }
        double value = cell.getCellType() == CellType.NUMERIC
                ? cell.getNumericCellValue()
                : Double.parseDouble(dataFormatter.formatCellValue(cell).replace(',', '.'));
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private LocalDate date(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() != CellType.NUMERIC) {
            return null;
        }
        LocalDateTime dateTime = DateUtil.isCellDateFormatted(cell)
                ? cell.getLocalDateTimeCellValue()
                : DateUtil.getLocalDateTime(cell.getNumericCellValue());
        return dateTime.toLocalDate();
    }

    private Integer integer(Row row, int col, String fieldName) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) Math.round(cell.getNumericCellValue());
        }
        String text = dataFormatter.formatCellValue(cell).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Rad " + (row.getRowNum() + 1) + ": kunde inte tolka " + fieldName + " ur \"" + text + "\"", e);
        }
    }

    private String text(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        String value = dataFormatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    public static final class RowMissingRequiredFieldsException extends RuntimeException {
        public RowMissingRequiredFieldsException(int rowNumber, boolean missingName, boolean missingQuantity) {
            super("Rad " + rowNumber + ": " + message(missingName, missingQuantity));
        }

        private static String message(boolean missingName, boolean missingQuantity) {
            if (missingName && missingQuantity) {
                return "Vinet måste ha ett namn och antal flaskor angivet";
            }
            if (missingName) {
                return "Vinet måste ha ett namn";
            }
            return "Vinet måste ha antal flaskor angivet";
        }
    }
}
