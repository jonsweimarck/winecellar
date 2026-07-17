package com.example.winecellar.web;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Rating;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import com.example.winecellar.domain.WineType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public String vinkällare(Model model) {
        model.addAttribute("viner", wineService.listWines());
        return "vinkallare";
    }

    @PostMapping("/wines")
    public String läggTillVin(
            @RequestParam String name,
            @RequestParam WineType wineType,
            @RequestParam String producer,
            @RequestParam String country,
            @RequestParam int vintage,
            @RequestParam int quantity,
            @RequestParam String location,
            Model model
    ) {
        wineService.save(Wine.builder()
                .name(name).wineType(wineType).producer(producer).country(country)
                .vintage(vintage).quantity(quantity).location(location)
                .build());
        model.addAttribute("viner", wineService.listWines());
        // Returnerar bara listfragmentet - htmx byter ut #vinlista, ingen sidladdning.
        return "vinkallare :: lista";
    }

    @PostMapping("/wines/{id}/antal")
    public String ändraAntal(@PathVariable Long id, @RequestParam int quantity, Model model) {
        Wine vin = wineService.findById(new WineId(id))
                .orElseThrow(() -> new IllegalArgumentException("Inget vin med id " + id));
        wineService.save(vin.withQuantity(quantity));
        model.addAttribute("viner", wineService.listWines());
        return "vinkallare :: lista";
    }

    @DeleteMapping("/wines/{id}")
    public String taBortVin(@PathVariable Long id, Model model) {
        wineService.removeWine(new WineId(id));
        model.addAttribute("viner", wineService.listWines());
        return "vinkallare :: lista";
    }

    @PostMapping("/wines/{id}/bild")
    public String laddaUppBild(@PathVariable Long id, @RequestParam("bild") MultipartFile bild, Model model) throws IOException {
        Wine vin = wineService.findById(new WineId(id))
                .orElseThrow(() -> new IllegalArgumentException("Inget vin med id " + id));
        wineService.save(vin.withImage(bild.getBytes(), bild.getContentType()));
        model.addAttribute("viner", wineService.listWines());
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
        return "redigera-vin";
    }

    /**
     * Tar emot alla fält som String/rått värde och tolkar dem själv istället
     * för att låta Spring binda direkt till Rating/LocalDate/BigDecimal -
     * ett tomt formulärfält (inget betyg valt, inget pris ifyllt) blir annars
     * en tom sträng som Spring försöker binda rakt av och kraschar på, inte
     * null. Samma sorts hantering som VinradParser gör för Excel-celler.
     */
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
            @RequestParam String location
    ) {
        Wine befintligt = wineService.findById(new WineId(id))
                .orElseThrow(() -> new IllegalArgumentException("Inget vin med id " + id));
        wineService.save(befintligt.toBuilder()
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
                .location(location)
                .build());
        return "redirect:/";
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
