package com.example.winecellar.application;

import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class WineService {

    private final WineRepository wineRepository;

    public WineService(WineRepository wineRepository) {
        this.wineRepository = wineRepository;
    }

    public Wine save(Wine wine) {
        return wineRepository.save(wine);
    }

    public List<Wine> listWines() {
        return wineRepository.findAll();
    }

    /**
     * Orkestrerar sök-/filter-/sorteringsvyn - körs i applikationslagret
     * (inte i WineController) eftersom Gherkin-scenarierna testar mot det
     * här lagret, inte mot HTTP (se README:s arbetsprocess). Bara
     * sortering byggd hittills - filter och fritextsökning är planerade
     * som separata tillägg till den här metoden, inte nya metoder.
     */
    public List<Wine> sök(Sorteringsfält sortering, SorteringsRiktning riktning) {
        List<Wine> resultat = new ArrayList<>(wineRepository.findAll());
        resultat.sort(sortering.comparator(riktning));
        return resultat;
    }

    public Optional<Wine> findById(WineId id) {
        return wineRepository.findById(id);
    }

    public void removeWine(WineId id) {
        wineRepository.deleteById(id);
    }
}
