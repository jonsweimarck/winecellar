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
 * VinradParser/ImportExcel förväntar sig (samma kolumnlayout, "Vin" som
 * fliknamn) - den omvända operationen av ImportExcel, se README:s
 * "Export av databasen till Excel". Körs manuellt, precis som
 * ImportExcel - inte en del av den körande applikationen.
 *
 * **Fullständig rundtripp för bilder (byggt 2026-07-22, på användarens
 * begäran).** Etiketter skrivs på TVÅ oberoende sätt:
 * 1. Som vanliga ankrade bilder i xlsx-filens "Bild"-kolumn (se
 *    VinradSkrivares klasskommentar) - bara JPEG/PNG/GIF, en visuell
 *    "titta i Excel"-bekvämlighet.
 * 2. Som riktiga bildfiler i WINECELLAR_LOCAL_IMAGE_FOLDER (samma
 *    miljövariabel som ImportExcel/Bildmatchare redan använder för att
 *    KOPPLA bilder vid import) - alla MIME-typer Bildmatchare känner
 *    igen, inklusive webp. Det är DEN HÄR mekanismen som gör rundtrippen
 *    fullständig: en efterföljande ImportExcel-körning mot samma mapp
 *    plockar upp filerna via Bildmatchares namnmatchning, precis som vid
 *    en vanlig import.
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
            System.err.println("Sätt WINECELLAR_LOCAL_IMAGE_FOLDER för att även skriva ut etiketterna som bildfiler i en mapp, se README.");
            System.exit(1);
        }
        String utfilSökväg = args[0];
        String jdbcUrl = args.length > 1 ? args[1] : Databaskoppling.jdbcUrlFrånMiljö();
        String användare = args.length > 2 ? args[2] : Databaskoppling.miljövariabelEllerStandard("POSTGRESQL_ADDON_USER", "winecellar");
        String lösenord = args.length > 3 ? args[3] : Databaskoppling.miljövariabelEllerStandard("POSTGRESQL_ADDON_PASSWORD", "winecellar");
        String bildmappSökväg = System.getenv("WINECELLAR_LOCAL_IMAGE_FOLDER");

        List<Wine> viner = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, användare, lösenord);
             PreparedStatement statement = connection.prepareStatement(SELECT_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                viner.add(läsVin(resultSet));
            }
        }
        System.out.println("Läste " + viner.size() + " viner från databasen.");

        if (bildmappSökväg != null && !bildmappSökväg.isBlank()) {
            skrivBildfiler(viner, bildmappSökväg);
        }

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

    /**
     * Skriver varje vins bild som en egen fil i bildmappen, döpt exakt
     * som vinets namn (samma namnmatchning som Bildmatchare använder vid
     * import) - det är denna mapp, inte xlsx-filens ankrade bilder, som
     * gör en efterföljande återimport bildmedveten.
     */
    private static void skrivBildfiler(List<Wine> viner, String bildmappSökväg) throws IOException {
        Path bildmapp = Path.of(bildmappSökväg);
        Files.createDirectories(bildmapp);
        varnaOmDubblettnamnMedBild(viner);

        int skrivna = 0;
        for (Wine vin : viner) {
            if (vin.image() == null) {
                continue;
            }
            String ändelse = Bildmatchare.ÄNDELSE_PER_MIME.get(vin.imageMimeType());
            if (ändelse == null) {
                System.out.println("Varning: bilden för \"" + vin.name() + "\" har MIME-typen \""
                        + vin.imageMimeType() + "\", som inte känns igen - hoppar över filskrivning "
                        + "(bilden bäddas fortfarande in i xlsx-filen om formatet stöds där).");
                continue;
            }
            Files.write(bildmapp.resolve(vin.name() + "." + ändelse), vin.image());
            skrivna++;
        }
        System.out.println("Skrev " + skrivna + " bildfiler till " + bildmappSökväg + ".");
    }

    private static void varnaOmDubblettnamnMedBild(List<Wine> viner) {
        Map<String, Long> antalPerNamn = viner.stream()
                .filter(Wine::harBild)
                .collect(Collectors.groupingBy(Wine::name, Collectors.counting()));
        antalPerNamn.forEach((namn, antal) -> {
            if (antal > 1) {
                System.out.println("Varning: " + antal + " viner heter \"" + namn
                        + "\" - bara den sist skrivna bildfilen blir kvar i mappen.");
            }
        });
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
