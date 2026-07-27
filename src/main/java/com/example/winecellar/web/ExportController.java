package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.infrastructure.excel.ImageMatcher;
import com.example.winecellar.infrastructure.excel.WineRowWriter;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Webbaserad export av den inloggade användarens vinlista - antingen som
 * `.xlsx` (WINE-22) eller som en zip med etikettbilder (WINE-23), se
 * ADR 0014. Ersätter den tidigare CLI-baserade `ExportExcel`/
 * `WINECELLAR_LOCAL_IMAGE_FOLDER` (borttagen i WINE-20). Samma
 * kolumnlayout/namnkonvention som importsidan (kommande, WINE-24/WINE-25)
 * förväntar sig.
 *
 * Scopead till inloggad användares egna viner via
 * {@code WineService.listWines(owner)} - ingen egen dataisoleringslogik
 * här, samma princip som resten av `web`-lagret (se WINE-13/WINE-14).
 */
@Controller
public class ExportController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType ZIP_MEDIA_TYPE = MediaType.parseMediaType("application/zip");

    private final WineService wineService;
    private final UserRepository userRepository;

    public ExportController(WineService wineService, UserRepository userRepository) {
        this.wineService = wineService;
        this.userRepository = userRepository;
    }

    @GetMapping("/export/xlsx")
    public ResponseEntity<byte[]> exportXlsx(Authentication authentication) throws IOException {
        UserId owner = CurrentUser.owner(authentication, userRepository);
        List<Wine> wines = wineService.listWines(owner).stream()
                .sorted(Comparator.comparing(Wine::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vinlista.xlsx\"")
                .body(writeWorkbook(wines));
    }

    @GetMapping("/export/bilder.zip")
    public ResponseEntity<byte[]> exportImagesZip(Authentication authentication) throws IOException {
        UserId owner = CurrentUser.owner(authentication, userRepository);
        List<Wine> wines = wineService.listWines(owner);

        return ResponseEntity.ok()
                .contentType(ZIP_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vinbilder.zip\"")
                .body(writeImagesZip(wines));
    }

    /**
     * En fil per vin som har en sparad bild, namngiven enligt
     * `ImageMatcher.fileNameStem` (samma konvention som WINE-24/WINE-25
     * kommer läsa tillbaka från). Okänd MIME-typ hoppas över (bör inte
     * kunna hända - `image`/`imageMimeType` sätts alltid tillsammans vid
     * uppladdning, se `WineController.withImageIfProvided`).
     *
     * En beräknad stam kan i sällsynta fall krocka mellan två viner (två
     * viner med exakt samma namn och utan fullständig identitet) -
     * `ZipOutputStream.putNextEntry` tillåter inte två poster med samma
     * namn (kastar `ZipException`). Löst genom att hoppa över den andra
     * (och senare) krockande bilden istället för att låta hela
     * nedladdningen krascha - samma "hellre hoppa över tyst än gissa/
     * krascha"-linje som `ImageMatcher`s egen tvetydighetshantering,
     * fast utan en motsvarande varningskanal (ingen konsol att skriva
     * till för en webbanvändare).
     */
    private byte[] writeImagesZip(List<Wine> wines) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Set<String> usedEntryNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (Wine wine : wines) {
                if (!wine.hasImage()) {
                    continue;
                }
                String extension = ImageMatcher.EXTENSION_BY_MIME.get(wine.imageMimeType());
                if (extension == null) {
                    continue;
                }
                String entryName = ImageMatcher.fileNameStem(wine.producer(), wine.name(), wine.vintage())
                        + "." + extension;
                if (!usedEntryNames.add(entryName)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(wine.image());
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    private byte[] writeWorkbook(List<Wine> wines) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(WineRowWriter.SHEET_NAME);
            WineRowWriter.writeHeaderRow(sheet);

            CellStyle dateFormat = workbook.createCellStyle();
            dateFormat.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            WineRowWriter writer = new WineRowWriter();

            int rowNumber = 1;
            for (Wine wine : wines) {
                writer.write(wine, sheet.createRow(rowNumber++), dateFormat);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
