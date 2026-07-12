package com.example.winecellar.web;

import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import com.example.winecellar.domain.WineType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
        wineService.save(new Wine(null, name, wineType, producer, country, vintage, quantity, location));
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
}
