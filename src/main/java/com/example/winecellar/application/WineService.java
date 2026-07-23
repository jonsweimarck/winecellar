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
     * här lagret, inte mot HTTP (se README:s arbetsprocess). Tre steg i
     * ordning: (1) baslista - antingen samtliga viner eller ett
     * fritextsökresultat, (2) filtrera baslistan på facetterna, (3)
     * sortera. Sorteringen appliceras alltid sist och skriver alltså över
     * den rankordning en fritextsökning eventuellt gav - se README:s
     * "Filtrering, sökning och sortering" för den medvetna avvägningen
     * (ingen separat "Relevans"-sortering byggd ännu).
     */
    public List<Wine> search(SearchCriteria criteria) {
        List<Wine> baseList = criteria.searchTerm() == null || criteria.searchTerm().isBlank()
                ? wineRepository.findAll()
                : wineRepository.search(criteria.searchTerm());
        List<Wine> result = baseList.stream()
                .filter(wine -> criteria.wineTypes().isEmpty() || criteria.wineTypes().contains(wine.wineType()))
                .filter(wine -> criteria.countries().isEmpty() || criteria.countries().contains(wine.country()))
                .filter(wine -> criteria.regions().isEmpty() || criteria.regions().contains(wine.region()))
                .filter(wine -> criteria.subregions().isEmpty() || criteria.subregions().contains(wine.subregion()))
                .collect(Collectors.toCollection(ArrayList::new));
        result.sort(criteria.sortField().comparator(criteria.sortDirection()));
        return result;
    }

    /**
     * Land->region->underregion-trädet för filterpanelens kryssrutor -
     * härlett fräscht från samtliga viner (statiska facetter, oavsett
     * aktivt filter, se README:s "Filtrering, sökning och sortering").
     * Ingen uppslagstabell - land/region/underregion är fri text på Wine
     * (se CLAUDE.md), så trädet byggs om vid varje anrop istället för att
     * lagras. TreeMap/TreeSet ger alfabetisk ordning på alla tre nivåer
     * utan extra sorteringssteg. Viner utan land (tillåtet sedan namn
     * blev det enda obligatoriska fältet, se CLAUDE.md) hoppas över helt
     * - TreeMap tillåter inte en null-nyckel, och ett vin utan land kan
     * ändå inte placeras i något land-/regiongren i trädet.
     */
    public List<OriginNode> originTree() {
        Map<String, Map<String, Set<String>>> byCountryAndRegion = new TreeMap<>();
        for (Wine wine : wineRepository.findAll()) {
            if (wine.country() == null) {
                continue;
            }
            Map<String, Set<String>> byRegion =
                    byCountryAndRegion.computeIfAbsent(wine.country(), k -> new TreeMap<>());
            if (wine.region() != null) {
                Set<String> subregions =
                        byRegion.computeIfAbsent(wine.region(), k -> new TreeSet<>());
                if (wine.subregion() != null) {
                    subregions.add(wine.subregion());
                }
            }
        }

        List<OriginNode> tree = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String>>> countryEntry : byCountryAndRegion.entrySet()) {
            List<OriginNode> regionNodes = new ArrayList<>();
            for (Map.Entry<String, Set<String>> regionEntry : countryEntry.getValue().entrySet()) {
                List<OriginNode> subregionNodes = regionEntry.getValue().stream()
                        .map(subregion -> new OriginNode(subregion, List.of()))
                        .toList();
                regionNodes.add(new OriginNode(regionEntry.getKey(), subregionNodes));
            }
            tree.add(new OriginNode(countryEntry.getKey(), regionNodes));
        }
        return tree;
    }

    public Optional<Wine> findById(WineId id) {
        return wineRepository.findById(id);
    }

    public void removeWine(WineId id) {
        wineRepository.deleteById(id);
    }

    /**
     * Hittar en möjlig/fullständig dubblett bland redan sparade viner, se
     * Wine.matchesIdentityOf(...)/hasCompleteIdentity() och WINE-6. Linjär
     * genomsökning av samtliga viner, precis som findAll()/originTree()
     * redan gör - samlingens storlek gör det inte värt en särskild fråga.
     */
    public DuplicateCheck checkForDuplicate(Wine candidate) {
        for (Wine existing : wineRepository.findAll()) {
            if (candidate.matchesIdentityOf(existing)) {
                return candidate.hasCompleteIdentity()
                        ? new DuplicateCheck.FullDuplicate(existing)
                        : new DuplicateCheck.PartialDuplicate(existing);
            }
        }
        return new DuplicateCheck.NoDuplicate();
    }

    /**
     * Ökar antalet flaskor på ett redan sparat vin med 1 - används av
     * dubblettvarningens "öka antal istället"-val (WINE-6). Null-antal
     * (aldrig satt) behandlas som 0.
     */
    public Wine increaseQuantity(WineId id) {
        Wine existing = wineRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inget vin med id " + id));
        int currentQuantity = existing.quantity() == null ? 0 : existing.quantity();
        return wineRepository.save(existing.withQuantity(currentQuantity + 1));
    }
}
