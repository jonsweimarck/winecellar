package com.example.winecellar.web;

import com.example.winecellar.application.WineService;
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
}
