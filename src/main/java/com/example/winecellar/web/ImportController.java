package com.example.winecellar.web;

import com.example.winecellar.application.DuplicateCheck;
import com.example.winecellar.application.ImportPreview;
import com.example.winecellar.application.ImportPreviewService;
import com.example.winecellar.application.RowCandidate;
import com.example.winecellar.application.RowIssue;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.infrastructure.excel.ImageMatcher;
import com.example.winecellar.infrastructure.excel.WineRowParser;
import com.example.winecellar.infrastructure.excel.WineRowWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Webbaserad import av vinlistan från en `.xlsx`-fil, i två steg
 * (WINE-24/WINE-25, se ADR 0014): en torrkörning/förhandsgranskning
 * (parsar och dubblettkontrollerar UTAN att spara något), och ett
 * commit-steg som faktiskt sparar enligt vald dubblettstrategi.
 *
 * Den uppladdade filen (och en valfri bildmapp, `webkitdirectory`)
 * skrivs till en temporär mapp på disk - bara sökvägen (inte bilddatan)
 * hålls i HTTP-sessionen mellan torrkörningen och commit-steget, för att
 * inte belasta serverns minne med potentiellt tiotals megabyte
 * bilddata per inloggad användare (se CLAUDE.md/ADR 0014 för
 * avvägningen kring ~100 bilder). Klientsidans Canvas-nedskalning
 * (`import.html`, samma teknik som etikettskanningen, WINE-5) håller
 * nere hur stora bilderna faktiskt blir innan de ens når servern.
 * Övergivna (aldrig committade) temp-mappar städas dels direkt här
 * (en ny torrkörning i samma session tar bort en tidigare, ej
 * committerad temp-mapp innan en ny skapas), dels av
 * {@link PendingImportCleanup} som ett säkerhetsnät för mappar från
 * sessioner som aldrig kom tillbaka alls (se ADR 0017, WINE-27).
 */
@Controller
public class ImportController {

    static final String SESSION_KEY_PENDING_IMPORT_PATH = "pendingImportPath";
    static final String TEMP_DIR_PREFIX = "winecellar-import-";

    private final ImportPreviewService importPreviewService;
    private final WineService wineService;
    private final UserRepository userRepository;

    public ImportController(
            ImportPreviewService importPreviewService, WineService wineService, UserRepository userRepository) {
        this.importPreviewService = importPreviewService;
        this.wineService = wineService;
        this.userRepository = userRepository;
    }

    @GetMapping("/import")
    public String importForm() {
        return "import";
    }

    @PostMapping("/import")
    public String preview(
            @RequestParam("fil") MultipartFile fil,
            @RequestParam(value = "bilder", required = false) List<MultipartFile> bilder,
            Model model, Authentication authentication, HttpServletRequest request) throws IOException {
        UserId owner = CurrentUser.owner(authentication, userRepository);

        List<RowCandidate> candidates = new ArrayList<>();
        List<RowIssue> issues = new ArrayList<>();
        try {
            parseRows(fil.getInputStream(), candidates, issues);
        } catch (Exception e) {
            model.addAttribute("error", "Kunde inte tolka filen som en Excel-fil (\"" + WineRowWriter.SHEET_NAME
                    + "\"-fliken saknas, eller filen är skadad).");
            return "import";
        }

        ImportPreview preview = importPreviewService.preview(candidates, issues, owner);

        // En tidigare, aldrig committerad torrkörning i samma session (t.ex.
        // användaren laddade upp fel fil och försöker igen) får annars sin
        // temp-mapp övergiven på disk - sökvägen skrivs bara över i
        // sessionen nedan, ingenting städade bort mappen tidigare.
        deletePreviousPendingImport(request);

        Path tempDir = stashUploadForCommit(fil, bilder);
        request.getSession().setAttribute(SESSION_KEY_PENDING_IMPORT_PATH, tempDir.toString());

        model.addAttribute("preview", preview);
        return "import";
    }

    /**
     * Läser tillbaka den torrkörda filen/bildmappen från temp-mappen
     * (ingen ny uppladdning behövs - se klasskommentaren) och sparar
     * enligt vald dubblettstrategi. `redirect:/import` (Post-Redirect-Get)
     * istället för att rendera "import" direkt härifrån - en uppdatering
     * av resultatsidan ska inte kunna trigga en ny, dubblerande
     * import-körning, till skillnad från torrkörningen ovan (som är
     * ofarlig att köra om, den sparar ju ingenting).
     */
    @PostMapping("/import/commit")
    public String commit(
            @RequestParam FullDuplicateStrategy fullDuplicateStrategy,
            @RequestParam PartialDuplicateStrategy partialDuplicateStrategy,
            Authentication authentication, HttpServletRequest request,
            RedirectAttributes redirectAttributes) throws IOException {
        UserId owner = CurrentUser.owner(authentication, userRepository);

        String pendingPath = (String) request.getSession().getAttribute(SESSION_KEY_PENDING_IMPORT_PATH);
        if (pendingPath == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Ingen påbörjad import hittades - ladda upp filen igen.");
            return "redirect:/import";
        }
        Path tempDir = Path.of(pendingPath);

        List<RowCandidate> candidates = new ArrayList<>();
        List<RowIssue> issues = new ArrayList<>();
        try (InputStream in = Files.newInputStream(tempDir.resolve("data.xlsx"))) {
            parseRows(in, candidates, issues);
        }

        Path imagesDir = tempDir.resolve("bilder");
        ImageMatcher imageMatcher = Files.isDirectory(imagesDir) ? new ImageMatcher(imagesDir) : null;

        List<RowCandidate> unique = importPreviewService.excludeFileDuplicates(candidates, issues);
        ImportResult result = applyStrategyAndSave(
                unique, issues.size(), owner, fullDuplicateStrategy, partialDuplicateStrategy, imageMatcher);

        deleteRecursively(tempDir);
        request.getSession().removeAttribute(SESSION_KEY_PENDING_IMPORT_PATH);

        redirectAttributes.addFlashAttribute("result", result);
        return "redirect:/import";
    }

    private ImportResult applyStrategyAndSave(
            List<RowCandidate> candidates, int skippedRows, UserId owner,
            FullDuplicateStrategy fullDuplicateStrategy, PartialDuplicateStrategy partialDuplicateStrategy,
            ImageMatcher imageMatcher) throws IOException {
        int imported = 0;
        int increased = 0;
        int skipped = skippedRows;

        for (RowCandidate candidate : candidates) {
            DuplicateCheck check = wineService.checkForDuplicate(candidate.wine(), owner);
            if (check instanceof DuplicateCheck.FullDuplicate full) {
                if (fullDuplicateStrategy == FullDuplicateStrategy.OKA_ANTAL) {
                    // WINE-28: lägg till radens EGET antal, inte en hårdkodad
                    // +1 (som increaseQuantity ensam hade gett) - annars blir
                    // sluttalet fel så fort den importerade raden anger fler
                    // än en flaska.
                    wineService.increaseQuantityBy(full.existing().id(), owner, candidate.wine().quantity());
                    increased++;
                } else {
                    skipped++;
                }
            } else if (check instanceof DuplicateCheck.PartialDuplicate partial) {
                switch (partialDuplicateStrategy) {
                    case OKA_ANTAL -> {
                        wineService.increaseQuantityBy(partial.existing().id(), owner, candidate.wine().quantity());
                        increased++;
                    }
                    case LAGG_TILL_SOM_NYTT -> {
                        saveWithImage(candidate.wine(), owner, imageMatcher);
                        imported++;
                    }
                    case HOPPA_OVER -> skipped++;
                }
            } else {
                saveWithImage(candidate.wine(), owner, imageMatcher);
                imported++;
            }
        }
        return new ImportResult(imported, increased, skipped);
    }

    private void saveWithImage(Wine candidate, UserId owner, ImageMatcher imageMatcher) throws IOException {
        Wine.Builder builder = candidate.toBuilder().owner(owner);
        if (imageMatcher != null) {
            ImageMatcher.Image image = imageMatcher.findImage(candidate.producer(), candidate.name(), candidate.vintage());
            if (image != null) {
                builder.image(image.data()).imageMimeType(image.mimeType());
            }
        }
        wineService.save(builder.build());
    }

    /**
     * Rad 0 är rubrikraden (samma konvention som `WineRowParser`/den
     * borttagna CLI-importen, se WINE-20) - hoppas alltid över. En rad
     * som inte kan tolkas alls (saknar namn, eller något annat fält är
     * trasigt - t.ex. ett okänt betygsvärde) räknas som överhoppad
     * istället för att låta EN dålig rad stoppa hela körningen. Delad
     * mellan torrkörningen (läser från den uppladdade `MultipartFile`)
     * och commit-steget (läser samma fil tillbaka från temp-mappen) -
     * exakt samma tolkning måste ske båda gångerna.
     *
     * WINE-34: varje överhoppad rad sparas som ett {@link RowIssue} med
     * det ursprungliga felmeddelandet, så att användaren ser exakt varför
     * raden inte kunde importeras.
     */
    private void parseRows(InputStream in, List<RowCandidate> candidates, List<RowIssue> issues) throws IOException {
        WineRowParser parser = new WineRowParser();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheet(WineRowWriter.SHEET_NAME);
            if (sheet == null) {
                throw new IllegalStateException("Hittar ingen flik som heter \"" + WineRowWriter.SHEET_NAME + "\"");
            }
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                int rowNumber = row.getRowNum() + 1;
                try {
                    Wine wine = parser.parse(row);
                    candidates.add(new RowCandidate(rowNumber, wine));
                } catch (RuntimeException e) {
                    String message = e.getMessage();
                    if (message == null || message.isBlank()) {
                        message = "Rad " + rowNumber + ": Raden gick inte att tolka. Kontrollera att kolumnerna stämmer.";
                    }
                    issues.add(new RowIssue(rowNumber, message));
                }
            }
        }
    }

    private void deletePreviousPendingImport(HttpServletRequest request) throws IOException {
        String pendingPath = (String) request.getSession().getAttribute(SESSION_KEY_PENDING_IMPORT_PATH);
        if (pendingPath != null) {
            deleteRecursively(Path.of(pendingPath));
        }
    }

    private Path stashUploadForCommit(MultipartFile fil, List<MultipartFile> bilder) throws IOException {
        Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
        Files.copy(fil.getInputStream(), tempDir.resolve("data.xlsx"));

        if (bilder != null && !bilder.isEmpty()) {
            Path imagesDir = Files.createDirectory(tempDir.resolve("bilder"));
            for (MultipartFile bild : bilder) {
                String originalFilename = bild.getOriginalFilename();
                if (bild.isEmpty() || originalFilename == null || originalFilename.isBlank()) {
                    continue;
                }
                String baseName = Path.of(originalFilename).getFileName().toString();
                Files.copy(bild.getInputStream(), imagesDir.resolve(baseName));
            }
        }
        return tempDir;
    }

    private void deleteRecursively(Path directory) throws IOException {
        // Mappen kan redan vara borta - t.ex. om PendingImportCleanup (WINE-27)
        // städade bort den som övergiven strax innan detta anrop hann köra.
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    // Best effort - en kvarglömd fil här fångas ändå upp av
                    // PendingImportCleanups svep vid nästa inloggning.
                }
            });
        }
    }

    enum FullDuplicateStrategy {
        OKA_ANTAL, HOPPA_OVER
    }

    enum PartialDuplicateStrategy {
        OKA_ANTAL, LAGG_TILL_SOM_NYTT, HOPPA_OVER
    }
}
