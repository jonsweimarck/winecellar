package com.example.winecellar.web;

import com.example.winecellar.application.DuplicateCheck;
import com.example.winecellar.application.LabelInterpretationResult;
import com.example.winecellar.application.LabelInterpretationService;
import com.example.winecellar.application.OriginNode;
import com.example.winecellar.application.SearchCriteria;
import com.example.winecellar.application.SortDirection;
import com.example.winecellar.application.SortField;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import com.example.winecellar.domain.WineType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class WineController {

    private final WineService wineService;
    private final LabelInterpretationService labelInterpretationService;

    public WineController(WineService wineService, LabelInterpretationService labelInterpretationService) {
        this.wineService = wineService;
        this.labelInterpretationService = labelInterpretationService;
    }

    @GetMapping("/")
    public String wineCellar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "NAME") SortField sort,
            @RequestParam(required = false, defaultValue = "ASCENDING") SortDirection direction,
            @RequestParam(required = false) Set<String> wineType,
            @RequestParam(required = false) Set<String> country,
            @RequestParam(required = false) Set<String> region,
            @RequestParam(required = false) Set<String> subregion,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model, Authentication authentication) {
        populateWineListModel(model, search, sort, direction, wineType, country, region, subregion, authentication);
        return "true".equals(hxRequest) ? "vinkallare :: lista" : "vinkallare";
    }

    /**
     * Delas av GET / och DELETE /wines/{id} - båda renderar samma
     * #vinlista-fragment utifrån samma sök-/filter-/sorteringstillstånd.
     * "Ta bort" skickade tidigare inget om aktivt tillstånd alls (fixat
     * 2026-07-22, se CLAUDE.md) - borttagningsknapparna postar nu med
     * exakt samma queryparametrar som verktygsraden, se vinkallare.html.
     */
    private void populateWineListModel(
            Model model, String search, SortField sort, SortDirection direction,
            Set<String> wineType, Set<String> country, Set<String> region, Set<String> subregion,
            Authentication authentication) {
        Set<String> selectedWineTypes = emptyIfNull(wineType);
        Set<String> selectedCountries = emptyIfNull(country);
        Set<String> selectedRegions = emptyIfNull(region);
        Set<String> selectedSubregions = emptyIfNull(subregion);

        SearchCriteria criteria = SearchCriteria.builder()
                .searchTerm(search)
                .sortField(sort).sortDirection(direction)
                .wineTypes(selectedWineTypes.stream().map(WineType::valueOf).collect(Collectors.toSet()))
                .countries(selectedCountries)
                .regions(selectedRegions)
                .subregions(selectedSubregions)
                .build();
        List<Wine> result = wineService.search(criteria);
        List<OriginNode> originTree = wineService.originTree();
        ExpandedNodes expanded = calculateExpandedNodes(originTree, selectedRegions, selectedSubregions);
        SearchView searchView = new SearchView(search, sort, direction, selectedWineTypes, selectedCountries, selectedRegions, selectedSubregions);

        model.addAttribute("wines", result);
        model.addAttribute("totalCount", wineService.listWines().size());
        model.addAttribute("search", search == null ? "" : search);
        model.addAttribute("sortFields", SortField.values());
        model.addAttribute("sort", sort);
        model.addAttribute("direction", direction);
        model.addAttribute("selectedWineTypes", selectedWineTypes);
        model.addAttribute("originTree", originTree);
        model.addAttribute("selectedCountries", selectedCountries);
        model.addAttribute("selectedRegions", selectedRegions);
        model.addAttribute("selectedSubregions", selectedSubregions);
        model.addAttribute("expandedCountries", expanded.countries());
        model.addAttribute("expandedRegions", expanded.regions());
        model.addAttribute("chips", buildChips(searchView));
        model.addAttribute("canEdit", hasAdminRole(authentication));
    }

    private static Set<String> emptyIfNull(Set<String> value) {
        return value == null ? Set.of() : value;
    }

    private static final Map<String, String> WINE_TYPE_LABEL = Map.of(
            "RED", "Rött",
            "WHITE", "Vitt",
            "ROSE", "Rosé",
            "SPARKLING", "Mousserande",
            "FORTIFIED", "Starkvin"
    );

    /**
     * En "chip" per aktivt sök-/filtervärde (Detta är den enda extra biten
     * utöver vad som diskuterades under mockup-godkännandet - flaggad då
     * som ett tillägg, byggd nu på användarens begäran). Varje chip länkar
     * till exakt samma vy MINUS det enskilda värdet - en vanlig
     * `<a href>`, inte htmx: chippens borttagning måste uppdatera hela
     * verktygsraden (kryssrutor, sökfält) också, inte bara listan, och de
     * ligger utanför #vinlista-fragmentet som en htmx-swap annars hade
     * varit begränsad till.
     */
    private static List<Chip> buildChips(SearchView searchView) {
        List<Chip> chips = new ArrayList<>();
        if (searchView.search() != null && !searchView.search().isBlank()) {
            chips.add(new Chip(searchTermLabel(searchView.search()), searchView.urlWithout("search", null)));
        }
        for (String wineType : searchView.wineTypes()) {
            chips.add(new Chip(WINE_TYPE_LABEL.getOrDefault(wineType, wineType), searchView.urlWithout("wineType", wineType)));
        }
        for (String country : searchView.countries()) {
            chips.add(new Chip(country, searchView.urlWithout("country", country)));
        }
        for (String region : searchView.regions()) {
            chips.add(new Chip(region, searchView.urlWithout("region", region)));
        }
        for (String subregion : searchView.subregions()) {
            chips.add(new Chip(subregion, searchView.urlWithout("subregion", subregion)));
        }
        return chips;
    }

    /**
     * Sökningen (`plainto_tsquery`) kräver OCH mellan flera ord, inte en
     * sammanhängande fras - citationstecken runt hela frasen (tidigare
     * variant) antydde därför något striktare än vad som faktiskt sker.
     * "+" mellan orden signalerar OCH utan att kräva förklarande text i
     * det trånga chip-utrymmet.
     */
    private static String searchTermLabel(String search) {
        return "Sök: " + String.join(" + ", search.trim().split("\\s+"));
    }

    private record Chip(String label, String removeUrl) {
    }

    /**
     * Nuvarande sök-/filter-/sorteringstillstånd, med förmågan att bygga
     * en URL för "samma vy, men utan det här enskilda värdet" - det
     * chipsen länkar till. facet/value är null-säkra: facet == "search"
     * utelämnar sökordet oavsett värde, annars tas bara det angivna
     * värdet bort ur just den facettens set - övriga värden i samma
     * facett (och alla andra facetter) behålls oförändrade.
     */
    private record SearchView(
            String search, SortField sort, SortDirection direction,
            Set<String> wineTypes, Set<String> countries, Set<String> regions, Set<String> subregions
    ) {
        String urlWithout(String facet, String value) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/");
            if (search != null && !search.isBlank() && !"search".equals(facet)) {
                builder.queryParam("search", search);
            }
            builder.queryParam("sort", sort.name());
            builder.queryParam("direction", direction.name());
            addAllExcept(builder, "wineType", wineTypes, facet, value);
            addAllExcept(builder, "country", countries, facet, value);
            addAllExcept(builder, "region", regions, facet, value);
            addAllExcept(builder, "subregion", subregions, facet, value);
            return builder.build().encode().toUriString();
        }

        private static void addAllExcept(
                UriComponentsBuilder builder, String param, Set<String> values, String facet, String valueToRemove) {
            for (String v : values) {
                if (param.equals(facet) && v.equals(valueToRemove)) {
                    continue;
                }
                builder.queryParam(param, v);
            }
        }
    }

    /**
     * Vilka land-/regionnivåer i filterträdet som ska visas uppfällda vid
     * rendering - ett land fälls upp om någon av dess regioner (eller
     * någon underregion i någon av dess regioner) är vald, en region
     * fälls upp om någon av dess underregioner är vald. Annars är valda
     * region-/underregionsfilter osynliga bakom en hopfälld gren nästa
     * gång filterpanelen öppnas (upptäckt av användaren mot produktionen
     * 2026-07-21).
     */
    private static ExpandedNodes calculateExpandedNodes(
            List<OriginNode> tree, Set<String> selectedRegions, Set<String> selectedSubregions) {
        Set<String> countries = new HashSet<>();
        Set<String> regions = new HashSet<>();
        for (OriginNode country : tree) {
            boolean countryHasSelectedChild = false;
            for (OriginNode region : country.children()) {
                boolean hasSelectedSubregion = region.children().stream()
                        .anyMatch(subregion -> selectedSubregions.contains(subregion.name()));
                if (hasSelectedSubregion) {
                    regions.add(region.name());
                }
                if (selectedRegions.contains(region.name()) || hasSelectedSubregion) {
                    countryHasSelectedChild = true;
                }
            }
            if (countryHasSelectedChild) {
                countries.add(country.name());
            }
        }
        return new ExpandedNodes(countries, regions);
    }

    private record ExpandedNodes(Set<String> countries, Set<String> regions) {
    }

    @GetMapping("/wines/nytt")
    public String newWineForm(Model model) {
        model.addAttribute("wine", Wine.builder().build());
        model.addAttribute("ratings", Rating.values());
        return "vin-formular";
    }

    /**
     * Tolkar ett foto av en etikett till ett osparat utkast (WINE-5) -
     * renderar om samma formulär, förifyllt med det som kunde
     * läsas/härledas, istället för att spara något (se
     * LabelInterpretationService/docs/adr/0012). Bara relevant vid
     * TILLÄGG av ett nytt vin, inte redigering - därför ingen motsvarande
     * rutt kopplad till redigeringsformuläret.
     */
    @PostMapping("/wines/tolka-etikett")
    public String interpretLabel(@RequestParam("bild") MultipartFile image, Model model) throws IOException {
        LabelInterpretationResult result = labelInterpretationService.interpret(image.getBytes(), image.getContentType());
        model.addAttribute("ratings", Rating.values());
        if (result instanceof LabelInterpretationResult.Interpreted interpreted) {
            model.addAttribute("wine", interpreted.draft());
            model.addAttribute("interpretedFields", interpreted.interpretedFields());
        } else {
            model.addAttribute("wine", Wine.builder().build());
            model.addAttribute("labelInterpretationFailed", true);
        }
        return "vin-formular";
    }

    @PostMapping("/wines")
    public String addWine(
            @RequestParam String name,
            @RequestParam(required = false) String wineType,
            @RequestParam(required = false) String producer,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String subregion,
            @RequestParam(required = false) String grapes,
            @RequestParam(required = false) String vintage,
            @RequestParam(required = false) String purchaseDate,
            @RequestParam(required = false) String price,
            @RequestParam(required = false) String quantity,
            @RequestParam(required = false) String purchaseReason,
            @RequestParam(required = false) String tastingNotes,
            @RequestParam(required = false) String ownRating,
            @RequestParam(required = false) String systembolagetProductNumber,
            @RequestParam(required = false) String systembolagetDescription,
            @RequestParam(required = false) String munskankarnaReview,
            @RequestParam(required = false) String munskankarnaRating,
            @RequestParam(required = false) String vivinoRating,
            @RequestParam(required = false) String otherReference,
            @RequestParam(required = false) String location,
            @RequestParam(value = "bild", required = false) MultipartFile image,
            @RequestParam(required = false) String confirmAdd,
            Model model
    ) throws IOException {
        Wine.Builder builder = applyFormFields(Wine.builder(),
                name, wineType, producer, country, region, subregion, grapes, vintage,
                purchaseDate, price, quantity, purchaseReason, tastingNotes, ownRating,
                systembolagetProductNumber, systembolagetDescription, munskankarnaReview,
                munskankarnaRating, vivinoRating, otherReference, location
        );
        Wine candidate = withImageIfProvided(builder, image).build();

        if (!"true".equals(confirmAdd)) {
            DuplicateCheck duplicateCheck = wineService.checkForDuplicate(candidate);
            if (duplicateCheck instanceof DuplicateCheck.FullDuplicate full) {
                return renderDuplicateWarning(model, candidate, full.existing(), true);
            }
            if (duplicateCheck instanceof DuplicateCheck.PartialDuplicate partial) {
                return renderDuplicateWarning(model, candidate, partial.existing(), false);
            }
        }
        wineService.save(candidate);
        return "redirect:/";
    }

    /**
     * Renderar om samma formulär (utan redirect) med en varningsruta i
     * stället för att spara - WINE-6. `candidate` är det ifyllda, ännu
     * osparade vinet (utan id), så användaren kan antingen ändra ett fält
     * och skicka in igen, eller (bara vid en möjlig, inte fullständig,
     * dubblett) bekräfta att det ska sparas som ett nytt vin ändå via
     * "confirmAdd"-knappen i vin-formular.html.
     */
    private static String renderDuplicateWarning(Model model, Wine candidate, Wine existing, boolean fullDuplicate) {
        model.addAttribute("wine", candidate);
        model.addAttribute("ratings", Rating.values());
        model.addAttribute("duplicateExisting", existing);
        model.addAttribute("duplicateIsFull", fullDuplicate);
        return "vin-formular";
    }

    /**
     * Ökar antalet på det befintliga vinet med 1 - det ena av de två
     * valen i dubblettvarningen (WINE-6), inte en återinförd generell
     * "ändra antal"-rutt (den togs medvetet bort, se CLAUDE.md) eftersom
     * den här bara någonsin lägger till exakt en flaska, från en specifik
     * varningsdialog.
     */
    @PostMapping("/wines/{id}/dubblett-oka-antal")
    public String increaseQuantityForDuplicate(@PathVariable Long id) {
        wineService.increaseQuantity(new WineId(id));
        return "redirect:/";
    }

    /**
     * Behåller aktivt filter/sökning/sortering efter en borttagning
     * (fixat 2026-07-22 - tidigare återställdes vyn alltid till
     * standardläget, se CLAUDE.md). Borttagningsknapparna postar nu med
     * samma queryparametrar som verktygsraden (se vinkallare.html:s
     * hx-delete), så samma @RequestParam-uppsättning som GET / tas emot
     * här också.
     */
    @DeleteMapping("/wines/{id}")
    public String removeWine(
            @PathVariable Long id,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "NAME") SortField sort,
            @RequestParam(required = false, defaultValue = "ASCENDING") SortDirection direction,
            @RequestParam(required = false) Set<String> wineType,
            @RequestParam(required = false) Set<String> country,
            @RequestParam(required = false) Set<String> region,
            @RequestParam(required = false) Set<String> subregion,
            Model model, Authentication authentication) {
        wineService.removeWine(new WineId(id));
        populateWineListModel(model, search, sort, direction, wineType, country, region, subregion, authentication);
        return "vinkallare :: lista";
    }

    @GetMapping("/wines/{id}/bild")
    @ResponseBody
    public ResponseEntity<byte[]> showImage(@PathVariable Long id) {
        Wine wine = wineService.findById(new WineId(id))
                .filter(Wine::hasImage)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(wine.imageMimeType()))
                .body(wine.image());
    }

    @GetMapping("/wines/{id}/redigera")
    public String editForm(@PathVariable Long id, Model model) {
        Wine wine = wineService.findById(new WineId(id))
                .orElseThrow(() -> new IllegalArgumentException("Inget vin med id " + id));
        model.addAttribute("wine", wine);
        model.addAttribute("ratings", Rating.values());
        return "vin-formular";
    }

    @PostMapping("/wines/{id}/redigera")
    public String saveEdit(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String wineType,
            @RequestParam(required = false) String producer,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String subregion,
            @RequestParam(required = false) String grapes,
            @RequestParam(required = false) String vintage,
            @RequestParam(required = false) String purchaseDate,
            @RequestParam(required = false) String price,
            @RequestParam(required = false) String quantity,
            @RequestParam(required = false) String purchaseReason,
            @RequestParam(required = false) String tastingNotes,
            @RequestParam(required = false) String ownRating,
            @RequestParam(required = false) String systembolagetProductNumber,
            @RequestParam(required = false) String systembolagetDescription,
            @RequestParam(required = false) String munskankarnaReview,
            @RequestParam(required = false) String munskankarnaRating,
            @RequestParam(required = false) String vivinoRating,
            @RequestParam(required = false) String otherReference,
            @RequestParam(required = false) String location,
            @RequestParam(value = "bild", required = false) MultipartFile image
    ) throws IOException {
        Wine existing = wineService.findById(new WineId(id))
                .orElseThrow(() -> new IllegalArgumentException("Inget vin med id " + id));
        Wine.Builder wine = applyFormFields(existing.toBuilder(),
                name, wineType, producer, country, region, subregion, grapes, vintage,
                purchaseDate, price, quantity, purchaseReason, tastingNotes, ownRating,
                systembolagetProductNumber, systembolagetDescription, munskankarnaReview,
                munskankarnaRating, vivinoRating, otherReference, location
        );
        wineService.save(withImageIfProvided(wine, image).build());
        return "redirect:/";
    }

    /**
     * Styr om "Lägg till vin"/Redigera/Ta bort visas i vinkallare.html.
     * Bara ett extra UI-lager - den faktiska åtkomstkontrollen sker i
     * SecurityConfig, som nekar READONLY-kontot dessa routes oavsett vad
     * som visas i gränssnittet.
     */
    private static boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Bilduppladdning är en del av samma formulär/spara-anrop som allt annat
     * nu (inte längre en separat POST /wines/{id}/bild) - ett tomt/oifyllt
     * filfält ska inte nolla ut en redan sparad bild, så bara ett faktiskt
     * valt filnamn (MultipartFile.isEmpty() == false) sätter image/
     * imageMimeType. Annars behåller Builder:n vad den redan hade (null vid
     * tillägg, befintlig bild vid redigering utan ny fil).
     */
    private static Wine.Builder withImageIfProvided(Wine.Builder builder, MultipartFile image) throws IOException {
        if (image != null && !image.isEmpty()) {
            return builder.image(image.getBytes()).imageMimeType(image.getContentType());
        }
        return builder;
    }

    /**
     * Delad av addWine och saveEdit - båda formulären har samma
     * fält, skillnaden är bara vilken Builder de startar från (tom vid
     * tillägg, befintligt.toBuilder() vid redigering). Tar emot ALLA fält
     * som rå String och tolkar dem själv istället för att låta Spring
     * binda direkt till WineType/Integer/Rating/LocalDate/BigDecimal - ett
     * tomt formulärfält blir annars en tom sträng som Spring försöker
     * binda rakt av och kraschar på, inte null. Samma sorts hantering som
     * WineRowParser gör för Excel-celler. Namn är sedan 2026-07-22 det enda
     * obligatoriska fältet (se CLAUDE.md) - övriga tolkas alla till null
     * om blanka, inklusive wineType/vintage/quantity/producer/country/
     * location som tidigare krävdes ifyllda.
     */
    private static Wine.Builder applyFormFields(
            Wine.Builder builder,
            String name, String wineType, String producer, String country,
            String region, String subregion, String grapes,
            String vintage, String purchaseDate, String price, String quantity,
            String purchaseReason, String tastingNotes, String ownRating,
            String systembolagetProductNumber, String systembolagetDescription,
            String munskankarnaReview, String munskankarnaRating, String vivinoRating,
            String otherReference, String location
    ) {
        return builder
                .name(name).wineType(parseWineType(wineType))
                .producer(blankToNull(producer)).country(blankToNull(country))
                .region(blankToNull(region)).subregion(blankToNull(subregion)).grapes(blankToNull(grapes))
                .vintage(parseInteger(vintage)).purchaseDate(parseDate(purchaseDate)).price(parseDecimal(price))
                .quantity(parseInteger(quantity))
                .purchaseReason(blankToNull(purchaseReason)).tastingNotes(blankToNull(tastingNotes))
                .ownRating(parseRating(ownRating))
                .systembolagetProductNumber(blankToNull(systembolagetProductNumber))
                .systembolagetDescription(blankToNull(systembolagetDescription))
                .munskankarnaReview(blankToNull(munskankarnaReview))
                .munskankarnaRating(parseRating(munskankarnaRating))
                .vivinoRating(parseDecimal(vivinoRating))
                .otherReference(blankToNull(otherReference))
                .location(blankToNull(location));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static WineType parseWineType(String value) {
        return blankToNull(value) == null ? null : WineType.valueOf(value);
    }

    private static Integer parseInteger(String value) {
        return blankToNull(value) == null ? null : Integer.valueOf(value.trim());
    }

    private static Rating parseRating(String value) {
        return blankToNull(value) == null ? null : Rating.valueOf(value);
    }

    private static LocalDate parseDate(String value) {
        return blankToNull(value) == null ? null : LocalDate.parse(value);
    }

    private static BigDecimal parseDecimal(String value) {
        return blankToNull(value) == null ? null : new BigDecimal(value);
    }
}
