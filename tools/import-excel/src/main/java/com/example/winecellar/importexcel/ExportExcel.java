package com.example.winecellar.importexcel;

import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Engångsexport av wines-tabellen -> en Excel-fil i samma format som
 * VinradParser/ImportExcel förväntar sig (samma kolumnlayout, "Vin" som
 * fliknamn) - den omvända operationen av ImportExcel, se README:s
 * "Export av databasen till Excel". Körs manuellt, precis som
 * ImportExcel - inte en del av den körande applikationen.
 *
 * Etiketter (image/image_mime_type) exporteras som vanliga ankrade
 * bilder i "Bild"-kolumnen (byggt 2026-07-22) - se VinradSkrivares
 * klasskommentar för skillnaden mot källfilens ursprungliga "bild i
 * cell" och den viktiga begränsningen att ImportExcel inte läser dessa
 * bilder tillbaka vid en återimport.
 *
 * En rad exporterad och sedan återimporterad utan att typ/producent/
 * land fylls i (möjligt sedan bara namnet blev obligatoriskt i
 * webb-UI:t, se CLAUDE.md) hoppas över av VinradParser vid återimport,
 * med en utskriven varning - samma "ofullständig utkastrad"-hantering
 * som redan finns, inte en ny begränsning som exporten inför.
 */
public final class ExportExcel {

    private static final String SHEET_NAMN = "Vin";

    private static final String SELECT_SQL = """
            SELECT name, wine_type, producer, country, region, subregion, grapes, vintage,
                   purchase_date, price, quantity, purchase_reason, tasting_notes, own_rating,
                   systembolaget_product_number, systembolaget_description, munskankarna_review,
                   munskankarna_rating, vivino_rating, other_reference, location,
                   image, image_mime_type
            FROM wines
            ORDER BY name
            """;

    private static final String[] RUBRIKER = {
            "Vintyp", "Land", "Region", "Underregion", "Druvor", "Producent", "Namn", "Årgång",
            "Bild", "Inköpsdatum", "Pris", "Antal", "Varför köpt", "Tasting notes", "Eget betyg",
            "Systembolagets prodnummer", "Systembolagets beskrivning", "Munskänkarnas bedömning",
            "Munskänkarnas betyg", "Vivino", "Annan referens", "Plats"
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Användning: ExportExcel <sökväg-till-utfil.xlsx> [jdbc-url] [användare] [lösenord]");
            System.err.println("Utan jdbc-url/användare/lösenord används POSTGRESQL_ADDON_*-miljövariabler (samma konvention som application.yml), annars localhost/winecellar.");
            System.exit(1);
        }
        String utfilSökväg = args[0];
        String jdbcUrl = args.length > 1 ? args[1] : Databaskoppling.jdbcUrlFrånMiljö();
        String användare = args.length > 2 ? args[2] : Databaskoppling.miljövariabelEllerStandard("POSTGRESQL_ADDON_USER", "winecellar");
        String lösenord = args.length > 3 ? args[3] : Databaskoppling.miljövariabelEllerStandard("POSTGRESQL_ADDON_PASSWORD", "winecellar");

        List<Wine> viner = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, användare, lösenord);
             PreparedStatement statement = connection.prepareStatement(SELECT_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                viner.add(läsVin(resultSet));
            }
        }
        System.out.println("Läste " + viner.size() + " viner från databasen.");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAMN);
            skrivRubrikrad(sheet);

            CellStyle datumformat = workbook.createCellStyle();
            datumformat.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            Drawing<?> ritning = sheet.createDrawingPatriarch();

            VinradSkrivare skrivare = new VinradSkrivare();
            int radnummer = 1;
            for (Wine vin : viner) {
                skrivare.skriv(vin, sheet.createRow(radnummer++), datumformat, ritning);
            }

            try (OutputStream ut = new FileOutputStream(utfilSökväg)) {
                workbook.write(ut);
            }
        }
        System.out.println("Skrev " + viner.size() + " viner till " + utfilSökväg + ".");
    }

    private static void skrivRubrikrad(Sheet sheet) {
        Row rubrikrad = sheet.createRow(0);
        for (int kolumn = 0; kolumn < RUBRIKER.length; kolumn++) {
            rubrikrad.createCell(kolumn).setCellValue(RUBRIKER[kolumn]);
        }
    }

    private static Wine läsVin(ResultSet resultSet) throws SQLException {
        return Wine.builder()
                .name(resultSet.getString("name"))
                .wineType(nullbarVinTyp(resultSet.getString("wine_type")))
                .producer(resultSet.getString("producer"))
                .country(resultSet.getString("country"))
                .region(resultSet.getString("region"))
                .subregion(resultSet.getString("subregion"))
                .grapes(resultSet.getString("grapes"))
                .vintage(resultSet.getObject("vintage", Integer.class))
                .purchaseDate(resultSet.getDate("purchase_date") == null ? null : resultSet.getDate("purchase_date").toLocalDate())
                .price(resultSet.getBigDecimal("price"))
                .quantity(resultSet.getObject("quantity", Integer.class))
                .purchaseReason(resultSet.getString("purchase_reason"))
                .tastingNotes(resultSet.getString("tasting_notes"))
                .ownRating(nullbartBetyg(resultSet.getString("own_rating")))
                .systembolagetProductNumber(resultSet.getString("systembolaget_product_number"))
                .systembolagetDescription(resultSet.getString("systembolaget_description"))
                .munskankarnaReview(resultSet.getString("munskankarna_review"))
                .munskankarnaRating(nullbartBetyg(resultSet.getString("munskankarna_rating")))
                .vivinoRating(resultSet.getBigDecimal("vivino_rating"))
                .otherReference(resultSet.getString("other_reference"))
                .location(resultSet.getString("location"))
                .image(resultSet.getBytes("image"))
                .imageMimeType(resultSet.getString("image_mime_type"))
                .build();
    }

    private static WineType nullbarVinTyp(String värde) {
        return värde == null ? null : WineType.valueOf(värde);
    }

    private static Rating nullbartBetyg(String värde) {
        return värde == null ? null : Rating.valueOf(värde);
    }

    private ExportExcel() {
    }
}
