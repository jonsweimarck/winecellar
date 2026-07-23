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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Engångsimport av Vinlista.xlsx -> wines-tabellen. Körs manuellt, se
 * README:s "Import av befintlig Excel-data". Skriver direkt via JDBC,
 * inte via WineService/HTTP - detta är ett fristående skript som körs en
 * gång mot en redan existerande databas (samma tabell som appen använder
 * via JpaWineRepository), inte en del av den körande applikationen.
 *
 * Etiketter kan valfritt importeras samtidigt: sätt miljövariabeln
 * WINECELLAR_LOCAL_IMAGE_FOLDER (döpt om från WINECELLAR_IMPORT_IMAGE_FOLDER
 * 2026-07-22, då ExportExcel började skriva till samma mapp - namnet ska
 * spegla att mappen delas åt båda hållen, inte bara vid import) till en
 * mapp med bildfiler döpta exakt som respektive vins name-fält (t.ex.
 * "Barolo.jpg"), se ImageMatcher. Miljövariabel istället för ett
 * positionellt argument, av samma skäl som POSTGRESQL_ADDON_*-uppgifterna
 * nedan - undviker PowerShells trassel med flervärdesargument i
 * -Dexec.args (se README).
 */
public final class ImportExcel {

    private static final String SHEET_NAME = "Vin";

    private static final String INSERT_SQL = """
            INSERT INTO wines (
                name, wine_type, producer, country, region, subregion, grapes, vintage,
                purchase_date, price, quantity, purchase_reason, tasting_notes, own_rating,
                systembolaget_product_number, systembolaget_description, munskankarna_review,
                munskankarna_rating, vivino_rating, other_reference, location, image, image_mime_type
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Användning: ImportExcel <sökväg-till-vinlista.xlsx> [jdbc-url] [användare] [lösenord]");
            System.err.println("Utan jdbc-url/användare/lösenord används POSTGRESQL_ADDON_*-miljövariabler (samma konvention som application.yml), annars localhost/winecellar.");
            System.err.println("Sätt WINECELLAR_LOCAL_IMAGE_FOLDER för att även koppla etiketter från en bildmapp, se README.");
            System.exit(1);
        }
        String excelPath = args[0];
        String jdbcUrl = args.length > 1 ? args[1] : DatabaseConnection.jdbcUrlFromEnvironment();
        String user = args.length > 2 ? args[2] : DatabaseConnection.environmentVariableOrDefault("POSTGRESQL_ADDON_USER", "winecellar");
        String password = args.length > 3 ? args[3] : DatabaseConnection.environmentVariableOrDefault("POSTGRESQL_ADDON_PASSWORD", "winecellar");
        String imageFolderPath = System.getenv("WINECELLAR_LOCAL_IMAGE_FOLDER");
        ImageMatcher imageMatcher = imageFolderPath == null || imageFolderPath.isBlank()
                ? null
                : new ImageMatcher(Path.of(imageFolderPath));

        List<Wine> wines = new ArrayList<>();
        int skipped = 0;
        int imagesMatched = 0;
        WineRowParser parser = new WineRowParser();

        try (InputStream in = new FileInputStream(excelPath); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IllegalStateException("Hittar ingen flik som heter \"" + SHEET_NAME + "\" i " + excelPath);
            }
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue; // rubrikrad
                }
                try {
                    Wine wine = parser.parse(row);
                    if (imageMatcher != null) {
                        ImageMatcher.Image image = imageMatcher.findImage(wine.name());
                        if (image != null) {
                            wine = wine.withImage(image.data(), image.mimeType());
                            imagesMatched++;
                        }
                    }
                    wines.add(wine);
                } catch (WineRowParser.RowMissingRequiredFieldsException e) {
                    System.out.println("Hoppar över: " + e.getMessage());
                    skipped++;
                }
            }
        }

        System.out.println("Tolkade " + wines.size() + " viner från " + excelPath + " (" + skipped + " rader överhoppade).");
        if (imageMatcher != null) {
            System.out.println(imagesMatched + " av " + wines.size() + " viner fick en etikett kopplad från " + imageFolderPath + ".");
            warnAboutDuplicateNamesWithImage(wines);
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                for (Wine wine : wines) {
                    bindParameters(statement, wine);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
            System.out.println("Sparade " + wines.size() + " viner i databasen.");
        }
    }

    private static void bindParameters(PreparedStatement statement, Wine wine) throws java.sql.SQLException {
        int i = 1;
        statement.setString(i++, wine.name());
        setNullableString(statement, i++, wine.wineType() == null ? null : wine.wineType().name());
        setNullableString(statement, i++, wine.producer());
        setNullableString(statement, i++, wine.country());
        setNullableString(statement, i++, wine.region());
        setNullableString(statement, i++, wine.subregion());
        setNullableString(statement, i++, wine.grapes());
        setNullableInteger(statement, i++, wine.vintage());
        setNullableDate(statement, i++, wine.purchaseDate());
        setNullableBigDecimal(statement, i++, wine.price());
        setNullableInteger(statement, i++, wine.quantity());
        setNullableString(statement, i++, wine.purchaseReason());
        setNullableString(statement, i++, wine.tastingNotes());
        setNullableRating(statement, i++, wine.ownRating());
        setNullableString(statement, i++, wine.systembolagetProductNumber());
        setNullableString(statement, i++, wine.systembolagetDescription());
        setNullableString(statement, i++, wine.munskankarnaReview());
        setNullableRating(statement, i++, wine.munskankarnaRating());
        setNullableBigDecimal(statement, i++, wine.vivinoRating());
        setNullableString(statement, i++, wine.otherReference());
        setNullableString(statement, i++, wine.location());
        setNullableImage(statement, i++, wine.image());
        setNullableString(statement, i, wine.imageMimeType());
    }

    private static void warnAboutDuplicateNamesWithImage(List<Wine> wines) {
        Map<String, Long> countByName = wines.stream()
                .filter(Wine::hasImage)
                .collect(Collectors.groupingBy(Wine::name, Collectors.counting()));
        countByName.forEach((name, count) -> {
            if (count > 1) {
                System.out.println("Varning: " + count + " viner heter \"" + name + "\" - samma etikett kopplades till alla.");
            }
        });
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableRating(PreparedStatement statement, int index, Rating rating) throws java.sql.SQLException {
        setNullableString(statement, index, rating == null ? null : rating.name());
    }

    private static void setNullableBigDecimal(PreparedStatement statement, int index, BigDecimal value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.NUMERIC);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setNullableImage(PreparedStatement statement, int index, byte[] image) throws java.sql.SQLException {
        if (image == null) {
            statement.setNull(index, Types.BINARY);
        } else {
            statement.setBytes(index, image);
        }
    }

    private static void setNullableDate(PreparedStatement statement, int index, LocalDate value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(value));
        }
    }

    private ImportExcel() {
    }
}
