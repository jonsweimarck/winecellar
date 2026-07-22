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
 * "Barolo.jpg"), se Bildmatchare. Miljövariabel istället för ett
 * positionellt argument, av samma skäl som POSTGRESQL_ADDON_*-uppgifterna
 * nedan - undviker PowerShells trassel med flervärdesargument i
 * -Dexec.args (se README).
 */
public final class ImportExcel {

    private static final String SHEET_NAMN = "Vin";

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
        String excelSökväg = args[0];
        String jdbcUrl = args.length > 1 ? args[1] : Databaskoppling.jdbcUrlFrånMiljö();
        String användare = args.length > 2 ? args[2] : Databaskoppling.miljövariabelEllerStandard("POSTGRESQL_ADDON_USER", "winecellar");
        String lösenord = args.length > 3 ? args[3] : Databaskoppling.miljövariabelEllerStandard("POSTGRESQL_ADDON_PASSWORD", "winecellar");
        String bildmappSökväg = System.getenv("WINECELLAR_LOCAL_IMAGE_FOLDER");
        Bildmatchare bildmatchare = bildmappSökväg == null || bildmappSökväg.isBlank()
                ? null
                : new Bildmatchare(Path.of(bildmappSökväg));

        List<Wine> viner = new ArrayList<>();
        int överhoppade = 0;
        int bilderMatchade = 0;
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
                    Wine vin = parser.parse(row);
                    if (bildmatchare != null) {
                        Bildmatchare.Bild bild = bildmatchare.hittaBild(vin.name());
                        if (bild != null) {
                            vin = vin.withImage(bild.data(), bild.mimeType());
                            bilderMatchade++;
                        }
                    }
                    viner.add(vin);
                } catch (VinradParser.RadSaknarObligatoriskaFältException e) {
                    System.out.println("Hoppar över: " + e.getMessage());
                    överhoppade++;
                }
            }
        }

        System.out.println("Tolkade " + viner.size() + " viner från " + excelSökväg + " (" + överhoppade + " rader överhoppade).");
        if (bildmatchare != null) {
            System.out.println(bilderMatchade + " av " + viner.size() + " viner fick en etikett kopplad från " + bildmappSökväg + ".");
            varnaOmDubblettnamnMedBild(viner);
        }

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
        settNullbarSträng(statement, i++, vin.wineType() == null ? null : vin.wineType().name());
        settNullbarSträng(statement, i++, vin.producer());
        settNullbarSträng(statement, i++, vin.country());
        settNullbarSträng(statement, i++, vin.region());
        settNullbarSträng(statement, i++, vin.subregion());
        settNullbarSträng(statement, i++, vin.grapes());
        settNullbartHeltal(statement, i++, vin.vintage());
        settNullbartDatum(statement, i++, vin.purchaseDate());
        settNullbarBigDecimal(statement, i++, vin.price());
        settNullbartHeltal(statement, i++, vin.quantity());
        settNullbarSträng(statement, i++, vin.purchaseReason());
        settNullbarSträng(statement, i++, vin.tastingNotes());
        settNullbartBetyg(statement, i++, vin.ownRating());
        settNullbarSträng(statement, i++, vin.systembolagetProductNumber());
        settNullbarSträng(statement, i++, vin.systembolagetDescription());
        settNullbarSträng(statement, i++, vin.munskankarnaReview());
        settNullbartBetyg(statement, i++, vin.munskankarnaRating());
        settNullbarBigDecimal(statement, i++, vin.vivinoRating());
        settNullbarSträng(statement, i++, vin.otherReference());
        settNullbarSträng(statement, i++, vin.location());
        settNullbarBild(statement, i++, vin.image());
        settNullbarSträng(statement, i, vin.imageMimeType());
    }

    private static void varnaOmDubblettnamnMedBild(List<Wine> viner) {
        Map<String, Long> antalPerNamn = viner.stream()
                .filter(Wine::harBild)
                .collect(Collectors.groupingBy(Wine::name, Collectors.counting()));
        antalPerNamn.forEach((namn, antal) -> {
            if (antal > 1) {
                System.out.println("Varning: " + antal + " viner heter \"" + namn + "\" - samma etikett kopplades till alla.");
            }
        });
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

    private static void settNullbartHeltal(PreparedStatement statement, int index, Integer värde) throws java.sql.SQLException {
        if (värde == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, värde);
        }
    }

    private static void settNullbarBild(PreparedStatement statement, int index, byte[] bild) throws java.sql.SQLException {
        if (bild == null) {
            statement.setNull(index, Types.BINARY);
        } else {
            statement.setBytes(index, bild);
        }
    }

    private static void settNullbartDatum(PreparedStatement statement, int index, LocalDate värde) throws java.sql.SQLException {
        if (värde == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(värde));
        }
    }

    private ImportExcel() {
    }
}
