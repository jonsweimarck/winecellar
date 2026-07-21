package com.example.winecellar.application;

import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
     * här lagret, inte mot HTTP (se README:s arbetsprocess). Fritextsökning
     * är planerad som ett tillägg till Sökkriterier, inte en ny metod.
     */
    public List<Wine> sök(Sökkriterier kriterier) {
        List<Wine> resultat = wineRepository.findAll().stream()
                .filter(vin -> kriterier.vintyper().isEmpty() || kriterier.vintyper().contains(vin.wineType()))
                .filter(vin -> kriterier.länder().isEmpty() || kriterier.länder().contains(vin.country()))
                .filter(vin -> kriterier.regioner().isEmpty() || kriterier.regioner().contains(vin.region()))
                .filter(vin -> kriterier.underregioner().isEmpty() || kriterier.underregioner().contains(vin.subregion()))
                .collect(Collectors.toCollection(ArrayList::new));
        resultat.sort(kriterier.sortering().comparator(kriterier.riktning()));
        return resultat;
    }

    /**
     * Land->region->underregion-trädet för filterpanelens kryssrutor -
     * härlett fräscht från samtliga viner (statiska facetter, oavsett
     * aktivt filter, se README:s "Filtrering, sökning och sortering").
     * Ingen uppslagstabell - land/region/underregion är fri text på Wine
     * (se CLAUDE.md), så trädet byggs om vid varje anrop istället för att
     * lagras. TreeMap/TreeSet ger alfabetisk ordning på alla tre nivåer
     * utan extra sorteringssteg.
     */
    public List<HärkomstNod> härkomstträd() {
        Map<String, Map<String, Set<String>>> perLandOchRegion = new TreeMap<>();
        for (Wine vin : wineRepository.findAll()) {
            Map<String, Set<String>> perRegion =
                    perLandOchRegion.computeIfAbsent(vin.country(), k -> new TreeMap<>());
            if (vin.region() != null) {
                Set<String> underregioner =
                        perRegion.computeIfAbsent(vin.region(), k -> new TreeSet<>());
                if (vin.subregion() != null) {
                    underregioner.add(vin.subregion());
                }
            }
        }

        List<HärkomstNod> träd = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String>>> landEntry : perLandOchRegion.entrySet()) {
            List<HärkomstNod> regionNoder = new ArrayList<>();
            for (Map.Entry<String, Set<String>> regionEntry : landEntry.getValue().entrySet()) {
                List<HärkomstNod> underregionNoder = regionEntry.getValue().stream()
                        .map(underregion -> new HärkomstNod(underregion, List.of()))
                        .toList();
                regionNoder.add(new HärkomstNod(regionEntry.getKey(), underregionNoder));
            }
            träd.add(new HärkomstNod(landEntry.getKey(), regionNoder));
        }
        return träd;
    }

    public Optional<Wine> findById(WineId id) {
        return wineRepository.findById(id);
    }

    public void removeWine(WineId id) {
        wineRepository.deleteById(id);
    }
}
