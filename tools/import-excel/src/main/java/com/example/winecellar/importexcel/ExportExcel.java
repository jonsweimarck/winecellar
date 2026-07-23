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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Engångsexport av wines-tabellen -> en Excel-fil i samma format som
 * WineRowParser/ImportExcel förväntar sig (samma kolumnlayout, "Vin" som
 * fliknamn) - den omvända operationen av ImportExcel, se README:s
 * "Export av databasen till Excel". Körs manuellt, precis som
 * ImportExcel - inte en del av den körande applikationen.
 *
 * **Fullständig rundtripp för bilder (byggt 2026-07-22, på användarens
 * begäran).** Etiketter skrivs på TVÅ oberoende sätt:
 * 1. Som vanliga ankrade bilder i xlsx-filens "Bild"-kolumn (se
 *    WineRowWriters klasskommentar) - bara JPEG/PNG/GIF, en visuell
 *    "titta i Excel"-bekvämlighet.
 * 2. Som riktiga bildfiler i WINECELLAR_LOCAL_IMAGE_FOLDER (samma
 *    miljövariabel som ImportExcel/ImageMatcher redan använder för att
 *    KOPPLA bilder vid import) - alla MIME-typer ImageMatcher känner
 *    igen, inklusive webp. Det är DEN HÄR mekanismen som gör rundtrippen
 *    fullständig: en efterföljande ImportExcel-körning mot samma mapp
 *    plockar upp filerna via ImageMatchers namnmatchning, precis som vid
 *    en vanlig import.
 */
public final class ExportExcel {

    private static final String SHEET_NAME = "Vin";

    private static final String SELECT_SQL = """
            SELECT name, wine_type, producer, country, region, subregion, grapes, vintage,
                   purchase_date, price, quantity, purchase_reason, tasting_notes, own_rating,
                   systembolaget_product_number, systembolaget_description, munskankarna_review,
                   munskankarna_rating, vivino_rating, other_reference, location,
                   image, image_mime_type
            FROM wines
            ORDER BY name
            """;

    private static final String[] HEADERS = {
            "Vintyp", "Land", "Region", "Underregion", "Druvor", "Producent", "Namn", "Årgång",
            "Bild", "Inköpsdatum", "Pris", "Antal", "Varför köpt", "Tasting notes", "Eget betyg",
            "Systembolagets prodnummer", "Systembolagets beskrivning", "Munskänkarnas bedömning",
            "Munskänkarnas betyg", "Vivino", "Annan referens", "Plats"
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Användning: ExportExcel <sökväg-till-utfil.xlsx> [jdbc-url] [användare] [lösenord]");
            System.err.println("Utan jdbc-url/användare/lösenord används POSTGRESQL_ADDON_*-miljövariabler (samma konvention som application.yml), annars localhost/winecellar.");
            System.err.println("Sätt WINECELLAR_LOCAL_IMAGE_FOLDER för att även skriva ut etiketterna som bildfiler i en mapp, se README.");
            System.exit(1);
        }
        String outputPath = args[0];
        String jdbcUrl = args.length > 1 ? args[1] : DatabaseConnection.jdbcUrlFromEnvironment();
        String user = args.length > 2 ? args[2] : DatabaseConnection.environmentVariableOrDefault("POSTGRESQL_ADDON_USER", "winecellar");
        String password = args.length > 3 ? args[3] : DatabaseConnection.environmentVariableOrDefault("POSTGRESQL_ADDON_PASSWORD", "winecellar");
        String imageFolderPath = System.getenv("WINECELLAR_LOCAL_IMAGE_FOLDER");

        List<Wine> wines = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement statement = connection.prepareStatement(SELECT_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                wines.add(readWine(resultSet));
            }
        }
        System.out.println("Läste " + wines.size() + " viner från databasen.");

        if (imageFolderPath != null && !imageFolderPath.isBlank()) {
            writeImageFiles(wines, imageFolderPath);
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            writeHeaderRow(sheet);

            CellStyle dateFormat = workbook.createCellStyle();
            dateFormat.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            Drawing<?> drawing = sheet.createDrawingPatriarch();

            WineRowWriter writer = new WineRowWriter();
            int rowNumber = 1;
            for (Wine wine : wines) {
                writer.write(wine, sheet.createRow(rowNumber++), dateFormat, drawing);
            }

            try (OutputStream out = new FileOutputStream(outputPath)) {
                workbook.write(out);
            }
        }
        System.out.println("Skrev " + wines.size() + " viner till " + outputPath + ".");
    }

    /**
     * Skriver varje vins bild som en egen fil i bildmappen, döpt exakt
     * som vinets namn (samma namnmatchning som ImageMatcher använder vid
     * import) - det är denna mapp, inte xlsx-filens ankrade bilder, som
     * gör en efterföljande återimport bildmedveten.
     */
    private static void writeImageFiles(List<Wine> wines, String imageFolderPath) throws IOException {
        Path imageFolder = Path.of(imageFolderPath);
        Files.createDirectories(imageFolder);
        warnAboutDuplicateNamesWithImage(wines);

        int written = 0;
        for (Wine wine : wines) {
            if (wine.image() == null) {
                continue;
            }
            String extension = ImageMatcher.EXTENSION_BY_MIME.get(wine.imageMimeType());
            if (extension == null) {
                System.out.println("Varning: bilden för \"" + wine.name() + "\" har MIME-typen \""
                        + wine.imageMimeType() + "\", som inte känns igen - hoppar över filskrivning "
                        + "(bilden bäddas fortfarande in i xlsx-filen om formatet stöds där).");
                continue;
            }
            Files.write(imageFolder.resolve(wine.name() + "." + extension), wine.image());
            written++;
        }
        System.out.println("Skrev " + written + " bildfiler till " + imageFolderPath + ".");
    }

    private static void warnAboutDuplicateNamesWithImage(List<Wine> wines) {
        Map<String, Long> countByName = wines.stream()
                .filter(Wine::hasImage)
                .collect(Collectors.groupingBy(Wine::name, Collectors.counting()));
        countByName.forEach((name, count) -> {
            if (count > 1) {
                System.out.println("Varning: " + count + " viner heter \"" + name
                        + "\" - bara den sist skrivna bildfilen blir kvar i mappen.");
            }
        });
    }

    private static void writeHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        for (int column = 0; column < HEADERS.length; column++) {
            headerRow.createCell(column).setCellValue(HEADERS[column]);
        }
    }

    private static Wine readWine(ResultSet resultSet) throws SQLException {
        return Wine.builder()
                .name(resultSet.getString("name"))
                .wineType(nullableWineType(resultSet.getString("wine_type")))
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
                .ownRating(nullableRating(resultSet.getString("own_rating")))
                .systembolagetProductNumber(resultSet.getString("systembolaget_product_number"))
                .systembolagetDescription(resultSet.getString("systembolaget_description"))
                .munskankarnaReview(resultSet.getString("munskankarna_review"))
                .munskankarnaRating(nullableRating(resultSet.getString("munskankarna_rating")))
                .vivinoRating(resultSet.getBigDecimal("vivino_rating"))
                .otherReference(resultSet.getString("other_reference"))
                .location(resultSet.getString("location"))
                .image(resultSet.getBytes("image"))
                .imageMimeType(resultSet.getString("image_mime_type"))
                .build();
    }

    private static WineType nullableWineType(String value) {
        return value == null ? null : WineType.valueOf(value);
    }

    private static Rating nullableRating(String value) {
        return value == null ? null : Rating.valueOf(value);
    }

    private ExportExcel() {
    }
}
