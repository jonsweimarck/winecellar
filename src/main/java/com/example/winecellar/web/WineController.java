package com.example.winecellar.web;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
public class WineController {

    private final WineService wineService;

    public WineController(WineService wineService) {
        this.wineService = wineService;
    }

    @GetMapping("/")
    public String vinkällare(Model model, Authentication authentication) {
        model.addAttribute("viner", wineService.listWines());
        model.addAttribute("kanRedigera", harRollAdmin(authentication));
        return "vinkallare";
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

    @DeleteMapping("/wines/{id}")
    public String taBortVin(@PathVariable Long id, Model model, Authentication authentication) {
        wineService.removeWine(new WineId(id));
        model.addAttribute("viner", wineService.listWines());
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
