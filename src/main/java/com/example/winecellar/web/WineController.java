package com.example.winecellar.web;

import com.example.winecellar.application.HärkomstNod;
import com.example.winecellar.application.SorteringsRiktning;
import com.example.winecellar.application.Sorteringsfält;
import com.example.winecellar.application.Sökkriterier;
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

    public WineController(WineService wineService) {
        this.wineService = wineService;
    }

    @GetMapping("/")
    public String vinkällare(
            @RequestParam(required = false) String sok,
            @RequestParam(required = false, defaultValue = "NAMN") Sorteringsfält sortera,
            @RequestParam(required = false, defaultValue = "STIGANDE") SorteringsRiktning riktning,
            @RequestParam(required = false) Set<String> wineType,
            @RequestParam(required = false) Set<String> country,
            @RequestParam(required = false) Set<String> region,
            @RequestParam(required = false) Set<String> subregion,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model, Authentication authentication) {
        Set<String> valdaVintyper = tomOmNull(wineType);
        Set<String> valdaLänder = tomOmNull(country);
        Set<String> valdaRegioner = tomOmNull(region);
        Set<String> valdaUnderregioner = tomOmNull(subregion);

        Sökkriterier kriterier = Sökkriterier.builder()
                .sökterm(sok)
                .sortering(sortera).riktning(riktning)
                .vintyper(valdaVintyper.stream().map(WineType::valueOf).collect(Collectors.toSet()))
                .länder(valdaLänder)
                .regioner(valdaRegioner)
                .underregioner(valdaUnderregioner)
                .build();
        List<Wine> resultat = wineService.sök(kriterier);
        List<HärkomstNod> härkomstträd = wineService.härkomstträd();
        ExpanderadeNoder expanderade = beräknaExpanderadeNoder(härkomstträd, valdaRegioner, valdaUnderregioner);
        Sökvy sökvy = new Sökvy(sok, sortera, riktning, valdaVintyper, valdaLänder, valdaRegioner, valdaUnderregioner);

        model.addAttribute("viner", resultat);
        model.addAttribute("antalTotalt", wineService.listWines().size());
        model.addAttribute("sok", sok == null ? "" : sok);
        model.addAttribute("sorteringsfält", Sorteringsfält.values());
        model.addAttribute("sortera", sortera);
        model.addAttribute("riktning", riktning);
        model.addAttribute("valdaVintyper", valdaVintyper);
        model.addAttribute("härkomstträd", härkomstträd);
        model.addAttribute("valdaLänder", valdaLänder);
        model.addAttribute("valdaRegioner", valdaRegioner);
        model.addAttribute("valdaUnderregioner", valdaUnderregioner);
        model.addAttribute("expanderadeLänder", expanderade.länder());
        model.addAttribute("expanderadeRegioner", expanderade.regioner());
        model.addAttribute("chips", byggChips(sökvy));
        model.addAttribute("kanRedigera", harRollAdmin(authentication));
        return "true".equals(hxRequest) ? "vinkallare :: lista" : "vinkallare";
    }

    private static Set<String> tomOmNull(Set<String> värde) {
        return värde == null ? Set.of() : värde;
    }

    private static final Map<String, String> VINTYP_ETIKETT = Map.of(
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
    private static List<Chip> byggChips(Sökvy sökvy) {
        List<Chip> chips = new ArrayList<>();
        if (sökvy.sok() != null && !sökvy.sok().isBlank()) {
            chips.add(new Chip("\"" + sökvy.sok() + "\"", sökvy.urlUtan("sok", null)));
        }
        for (String vintyp : sökvy.vintyper()) {
            chips.add(new Chip(VINTYP_ETIKETT.getOrDefault(vintyp, vintyp), sökvy.urlUtan("wineType", vintyp)));
        }
        for (String land : sökvy.länder()) {
            chips.add(new Chip(land, sökvy.urlUtan("country", land)));
        }
        for (String region : sökvy.regioner()) {
            chips.add(new Chip(region, sökvy.urlUtan("region", region)));
        }
        for (String underregion : sökvy.underregioner()) {
            chips.add(new Chip(underregion, sökvy.urlUtan("subregion", underregion)));
        }
        return chips;
    }

    private record Chip(String etikett, String taBortUrl) {
    }

    /**
     * Nuvarande sök-/filter-/sorteringstillstånd, med förmågan att bygga
     * en URL för "samma vy, men utan det här enskilda värdet" - det
     * chipsen länkar till. facett/värde är null-säkra: facett == "sok"
     * utelämnar sökordet oavsett värde, annars tas bara det angivna
     * värdet bort ur just den facettens set - övriga värden i samma
     * facett (och alla andra facetter) behålls oförändrade.
     */
    private record Sökvy(
            String sok, Sorteringsfält sortera, SorteringsRiktning riktning,
            Set<String> vintyper, Set<String> länder, Set<String> regioner, Set<String> underregioner
    ) {
        String urlUtan(String facett, String värde) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/");
            if (sok != null && !sok.isBlank() && !"sok".equals(facett)) {
                builder.queryParam("sok", sok);
            }
            builder.queryParam("sortera", sortera.name());
            builder.queryParam("riktning", riktning.name());
            läggTillUtom(builder, "wineType", vintyper, facett, värde);
            läggTillUtom(builder, "country", länder, facett, värde);
            läggTillUtom(builder, "region", regioner, facett, värde);
            läggTillUtom(builder, "subregion", underregioner, facett, värde);
            return builder.build().encode().toUriString();
        }

        private static void läggTillUtom(
                UriComponentsBuilder builder, String param, Set<String> värden, String facett, String taBortVärde) {
            for (String v : värden) {
                if (param.equals(facett) && v.equals(taBortVärde)) {
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
    private static ExpanderadeNoder beräknaExpanderadeNoder(
            List<HärkomstNod> träd, Set<String> valdaRegioner, Set<String> valdaUnderregioner) {
        Set<String> länder = new HashSet<>();
        Set<String> regioner = new HashSet<>();
        for (HärkomstNod land : träd) {
            boolean landHarValtBarn = false;
            for (HärkomstNod region : land.barn()) {
                boolean harValdUnderregion = region.barn().stream()
                        .anyMatch(underregion -> valdaUnderregioner.contains(underregion.namn()));
                if (harValdUnderregion) {
                    regioner.add(region.namn());
                }
                if (valdaRegioner.contains(region.namn()) || harValdUnderregion) {
                    landHarValtBarn = true;
                }
            }
            if (landHarValtBarn) {
                länder.add(land.namn());
            }
        }
        return new ExpanderadeNoder(länder, regioner);
    }

    private record ExpanderadeNoder(Set<String> länder, Set<String> regioner) {
    }

    @GetMapping("/wines/nytt")
    public String nyttVinFormulär(Model model) {
        model.addAttribute("vin", Wine.builder().build());
        model.addAttribute("betyg", Rating.values());
        return "vin-formular";
    }

    @PostMapping("/wines")
    public String läggTillVin(
            @RequestParam String name,
            @RequestParam WineType wineType,
            @RequestParam String producer,
            @RequestParam String country,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String subregion,
            @RequestParam(required = false) String grapes,
            @RequestParam int vintage,
            @RequestParam(required = false) String purchaseDate,
            @RequestParam(required = false) String price,
            @RequestParam int quantity,
            @RequestParam(required = false) String purchaseReason,
            @RequestParam(required = false) String tastingNotes,
            @RequestParam(required = false) String ownRating,
            @RequestParam(required = false) String systembolagetProductNumber,
            @RequestParam(required = false) String systembolagetDescription,
            @RequestParam(required = false) String munskankarnaReview,
            @RequestParam(required = false) String munskankarnaRating,
            @RequestParam(required = false) String vivinoRating,
            @RequestParam(required = false) String otherReference,
            @RequestParam String location,
            @RequestParam(value = "bild", required = false) MultipartFile bild
    ) throws IOException {
        Wine.Builder vin = tillämpaFormulärfält(Wine.builder(),
                name, wineType, producer, country, region, subregion, grapes, vintage,
                purchaseDate, price, quantity, purchaseReason, tastingNotes, ownRating,
                systembolagetProductNumber, systembolagetDescription, munskankarnaReview,
                munskankarnaRating, vivinoRating, otherReference, location
        );
        wineService.save(medBildOmVald(vin, bild).build());
        return "redirect:/";
    }

    /**
     * Visar alltid den ofiltrerade/osorterade listan efter borttagning
     * (oförändrat beteende sedan innan filter/sortering fanns) - "Ta
     * bort" skickar inget om aktivt filter/sökning/sortering (knapparna
     * ligger utanför verktygsradens <form>), så en eventuell aktiv vy
     * återställs till standardläget efter en borttagning. Känd
     * begränsning, inte löst nu - se CLAUDE.md.
     * `antalTotalt`/`chips` måste ändå sättas (tomma/lika med antalet
     * viner) eftersom #vinlista-fragmentet numera refererar till dem
     * ovillkorligt - annars kraschar renderingen med en
     * SpelEvaluationException (upptäckt av ett test, inte manuellt).
     */
    @DeleteMapping("/wines/{id}")
    public String taBortVin(@PathVariable Long id, Model model, Authentication authentication) {
        wineService.removeWine(new WineId(id));
        List<Wine> viner = wineService.listWines();
        model.addAttribute("viner", viner);
        model.addAttribute("antalTotalt", viner.size());
        model.addAttribute("chips", List.of());
        model.addAttribute("kanRedigera", harRollAdmin(authentication));
        return "vinkallare :: lista";
    }

    @GetMapping("/wines/{id}/bild")
    @ResponseBody
    public ResponseEntity<byte[]> visaBild(@PathVariable Long id) {
        Wine vin = wineService.findById(new WineId(id))
                .filter(Wine::harBild)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(vin.imageMimeType()))
                .body(vin.image());
    }

    @GetMapping("/wines/{id}/redigera")
    public String redigeraFormulär(@PathVariable Long id, Model model) {
        Wine vin = wineService.findById(new WineId(id))
                .orElseThrow(() -> new IllegalArgumentException("Inget vin med id " + id));
        model.addAttribute("vin", vin);
        model.addAttribute("betyg", Rating.values());
        return "vin-formular";
    }

    @PostMapping("/wines/{id}/redigera")
    public String sparaRedigering(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam WineType wineType,
            @RequestParam String producer,
            @RequestParam String country,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String subregion,
            @RequestParam(required = false) String grapes,
            @RequestParam int vintage,
            @RequestParam(required = false) String purchaseDate,
            @RequestParam(required = false) String price,
            @RequestParam int quantity,
            @RequestParam(required = false) String purchaseReason,
            @RequestParam(required = false) String tastingNotes,
            @RequestParam(required = false) String ownRating,
            @RequestParam(required = false) String systembolagetProductNumber,
            @RequestParam(required = false) String systembolagetDescription,
            @RequestParam(required = false) String munskankarnaReview,
            @RequestParam(required = false) String munskankarnaRating,
            @RequestParam(required = false) String vivinoRating,
            @RequestParam(required = false) String otherReference,
            @RequestParam String location,
            @RequestParam(value = "bild", required = false) MultipartFile bild
    ) throws IOException {
        Wine befintligt = wineService.findById(new WineId(id))
                .orElseThrow(() -> new IllegalArgumentException("Inget vin med id " + id));
        Wine.Builder vin = tillämpaFormulärfält(befintligt.toBuilder(),
                name, wineType, producer, country, region, subregion, grapes, vintage,
                purchaseDate, price, quantity, purchaseReason, tastingNotes, ownRating,
                systembolagetProductNumber, systembolagetDescription, munskankarnaReview,
                munskankarnaRating, vivinoRating, otherReference, location
        );
        wineService.save(medBildOmVald(vin, bild).build());
        return "redirect:/";
    }

    /**
     * Styr om "Lägg till vin"/Redigera/Ta bort visas i vinkallare.html.
     * Bara ett extra UI-lager - den faktiska åtkomstkontrollen sker i
     * SecurityConfig, som nekar READONLY-kontot dessa routes oavsett vad
     * som visas i gränssnittet.
     */
    private static boolean harRollAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(auktoritet -> auktoritet.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Bilduppladdning är en del av samma formulär/spara-anrop som allt annat
     * nu (inte längre en separat POST /wines/{id}/bild) - ett tomt/oifyllt
     * filfält ska inte nolla ut en redan sparad bild, så bara ett faktiskt
     * valt filnamn (MultipartFile.isEmpty() == false) sätter image/
     * imageMimeType. Annars behåller Builder:n vad den redan hade (null vid
     * tillägg, befintlig bild vid redigering utan ny fil).
     */
    private static Wine.Builder medBildOmVald(Wine.Builder builder, MultipartFile bild) throws IOException {
        if (bild != null && !bild.isEmpty()) {
            return builder.image(bild.getBytes()).imageMimeType(bild.getContentType());
        }
        return builder;
    }

    /**
     * Delad av läggTillVin och sparaRedigering - båda formulären har samma
     * fält, skillnaden är bara vilken Builder de startar från (tom vid
     * tillägg, befintligt.toBuilder() vid redigering). Tar emot valfria
     * fält som rå String och tolkar dem själv istället för att låta Spring
     * binda direkt till Rating/LocalDate/BigDecimal - ett tomt formulärfält
     * (inget betyg valt, inget pris ifyllt) blir annars en tom sträng som
     * Spring försöker binda rakt av och kraschar på, inte null. Samma sorts
     * hantering som VinradParser gör för Excel-celler.
     */
    private static Wine.Builder tillämpaFormulärfält(
            Wine.Builder builder,
            String name, WineType wineType, String producer, String country,
            String region, String subregion, String grapes,
            int vintage, String purchaseDate, String price, int quantity,
            String purchaseReason, String tastingNotes, String ownRating,
            String systembolagetProductNumber, String systembolagetDescription,
            String munskankarnaReview, String munskankarnaRating, String vivinoRating,
            String otherReference, String location
    ) {
        return builder
                .name(name).wineType(wineType).producer(producer).country(country)
                .region(tomBlirNull(region)).subregion(tomBlirNull(subregion)).grapes(tomBlirNull(grapes))
                .vintage(vintage).purchaseDate(tolkaDatum(purchaseDate)).price(tolkaDecimal(price))
                .quantity(quantity)
                .purchaseReason(tomBlirNull(purchaseReason)).tastingNotes(tomBlirNull(tastingNotes))
                .ownRating(tolkaBetyg(ownRating))
                .systembolagetProductNumber(tomBlirNull(systembolagetProductNumber))
                .systembolagetDescription(tomBlirNull(systembolagetDescription))
                .munskankarnaReview(tomBlirNull(munskankarnaReview))
                .munskankarnaRating(tolkaBetyg(munskankarnaRating))
                .vivinoRating(tolkaDecimal(vivinoRating))
                .otherReference(tomBlirNull(otherReference))
                .location(location);
    }

    private static String tomBlirNull(String värde) {
        return (värde == null || värde.isBlank()) ? null : värde;
    }

    private static Rating tolkaBetyg(String värde) {
        return tomBlirNull(värde) == null ? null : Rating.valueOf(värde);
    }

    private static LocalDate tolkaDatum(String värde) {
        return tomBlirNull(värde) == null ? null : LocalDate.parse(värde);
    }

    private static BigDecimal tolkaDecimal(String värde) {
        return tomBlirNull(värde) == null ? null : new BigDecimal(värde);
    }
}
