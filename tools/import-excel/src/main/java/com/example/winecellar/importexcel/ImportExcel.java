package com.example.winecellar.importexcel;

import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.Wine;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Engångsimport av Vinlista.xlsx -> wines-tabellen. Körs manuellt, se
 * README:s "Import av befintlig Excel-data". Skriver direkt via JDBC,
 * inte via WineService/HTTP - detta är ett fristående skript som körs en
 * gång mot en redan existerande databas (samma tabell som appen använder
 * via JpaWineRepository), inte en del av den körande applikationen.
 *
 * Bild-kolumnen importeras medvetet inte, se VinradParser - ladda upp
 * etiketter manuellt via webb-UI:t (POST /wines/{id}/bild) efteråt.
 */
public final class ImportExcel {

    private static final String SHEET_NAMN = "Vin";

    private static final String INSERT_SQL = """
            INSERT INTO wines (
                name, wine_type, producer, country, region, subregion, grapes, vintage,
                purchase_date, price, quantity, purchase_reason, tasting_notes, own_rating,
                systembolaget_product_number, systembolaget_description, munskankarna_review,
                munskankarna_rating, vivino_rating, other_reference, location
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Användning: ImportExcel <sökväg-till-vinlista.xlsx> [jdbc-url] [användare] [lösenord]");
            System.err.println("Utan jdbc-url/användare/lösenord används POSTGRESQL_ADDON_*-miljövariabler (samma konvention som application.yml), annars localhost/winecellar.");
            System.exit(1);
        }
        String excelSökväg = args[0];
        String jdbcUrl = args.length > 1 ? args[1] : jdbcUrlFrånMiljö();
        String användare = args.length > 2 ? args[2] : miljövariabelEllerStandard("POSTGRESQL_ADDON_USER", "winecellar");
        String lösenord = args.length > 3 ? args[3] : miljövariabelEllerStandard("POSTGRESQL_ADDON_PASSWORD", "winecellar");

        List<Wine> viner = new ArrayList<>();
        int överhoppade = 0;
        VinradParser parser = new VinradParser();

        try (InputStream in = new FileInputStream(excelSökväg); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheet(SHEET_NAMN);
            if (sheet == null) {
                throw new IllegalStateException("Hittar ingen flik som heter \"" + SHEET_NAMN + "\" i " + excelSökväg);
            }
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue; // rubrikrad
                }
                try {
                    viner.add(parser.parse(row));
                } catch (VinradParser.RadSaknarObligatoriskaFältException e) {
                    System.out.println("Hoppar över: " + e.getMessage());
                    överhoppade++;
                }
            }
        }

        System.out.println("Tolkade " + viner.size() + " viner från " + excelSökväg + " (" + överhoppade + " rader överhoppade).");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, användare, lösenord)) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                for (Wine vin : viner) {
                    bindParametrar(statement, vin);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
            System.out.println("Sparade " + viner.size() + " viner i databasen.");
        }
    }

    private static void bindParametrar(PreparedStatement statement, Wine vin) throws java.sql.SQLException {
        int i = 1;
        statement.setString(i++, vin.name());
        statement.setString(i++, vin.wineType().name());
        statement.setString(i++, vin.producer());
        statement.setString(i++, vin.country());
        settNullbarSträng(statement, i++, vin.region());
        settNullbarSträng(statement, i++, vin.subregion());
        settNullbarSträng(statement, i++, vin.grapes());
        statement.setInt(i++, vin.vintage());
        settNullbartDatum(statement, i++, vin.purchaseDate());
        settNullbarBigDecimal(statement, i++, vin.price());
        statement.setInt(i++, vin.quantity());
        settNullbarSträng(statement, i++, vin.purchaseReason());
        settNullbarSträng(statement, i++, vin.tastingNotes());
        settNullbartBetyg(statement, i++, vin.ownRating());
        settNullbarSträng(statement, i++, vin.systembolagetProductNumber());
        settNullbarSträng(statement, i++, vin.systembolagetDescription());
        settNullbarSträng(statement, i++, vin.munskankarnaReview());
        settNullbartBetyg(statement, i++, vin.munskankarnaRating());
        settNullbarBigDecimal(statement, i++, vin.vivinoRating());
        settNullbarSträng(statement, i++, vin.otherReference());
        settNullbarSträng(statement, i, vin.location());
    }

    private static void settNullbarSträng(PreparedStatement statement, int index, String värde) throws java.sql.SQLException {
        if (värde == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, värde);
        }
    }

    private static void settNullbartBetyg(PreparedStatement statement, int index, Rating rating) throws java.sql.SQLException {
        settNullbarSträng(statement, index, rating == null ? null : rating.name());
    }

    private static void settNullbarBigDecimal(PreparedStatement statement, int index, BigDecimal värde) throws java.sql.SQLException {
        if (värde == null) {
            statement.setNull(index, Types.NUMERIC);
        } else {
            statement.setBigDecimal(index, värde);
        }
    }

    private static void settNullbartDatum(PreparedStatement statement, int index, LocalDate värde) throws java.sql.SQLException {
        if (värde == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(värde));
        }
    }

    private static String jdbcUrlFrånMiljö() {
        String host = miljövariabelEllerStandard("POSTGRESQL_ADDON_HOST", "localhost");
        String port = miljövariabelEllerStandard("POSTGRESQL_ADDON_PORT", "5432");
        String db = miljövariabelEllerStandard("POSTGRESQL_ADDON_DB", "winecellar");
        return "jdbc:postgresql://" + host + ":" + port + "/" + db;
    }

    private static String miljövariabelEllerStandard(String namn, String standardvärde) {
        String värde = System.getenv(namn);
        return värde == null || värde.isBlank() ? standardvärde : värde;
    }

    private ImportExcel() {
    }
}
