package com.example.winecellar.application;

import com.example.winecellar.domain.Wine;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WINE-56, fjärde granskningsfyndet (se docs/adr/0024-chat-wine-mention-links.md):
 * en riktig produktionsexport avslöjade att ett lagrat vinnamn kan ha ett
 * skräp-mellanslag (t.ex. inklistrat från ett kalkylblad). Utan
 * mellanslags-tolerans i namnfacettens jämförelse hittar en i övrigt korrekt
 * genererad, trimmad namnlänk (se {@code ChatWineMentionLinker}) aldrig ett
 * sådant vin. Ett rent applikationslager-test, i linje med
 * ADR 0006 (orkestreringen ligger i {@link WineService}, inte i
 * controllern) - {@code SearchAndFilterSteps} täcker redan namnfacettens
 * vanliga OCH/ELLER-beteende via Gherkin, men Cucumbers egen tabellparsning
 * trimmar cellinnehåll automatiskt och kan alltså inte uttrycka ett
 * medvetet skräp-mellanslag i testdatan.
 */
class WineServiceTest {

    @Test
    void namnfacettenSkaMatchaEttLagratVinnamnMedAvslutandeSkräpMellanslag() {
        WineService wineService = new WineService(new InMemoryWineRepository());
        wineService.save(Wine.builder().name("Etna Bianco ").quantity(1).build());
        wineService.save(Wine.builder().name("Chablis").quantity(1).build());

        List<Wine> result = wineService.search(
                SearchCriteria.builder().names(Set.of("Etna Bianco")).build(), null);

        assertThat(result).extracting(wine -> wine.name().trim()).containsExactly("Etna Bianco");
    }

    @Test
    void namnfacettenSkaMatchaÄvenNärDetEfterfrågadeNamnetSjälvtHarOmgivandeMellanslag() {
        WineService wineService = new WineService(new InMemoryWineRepository());
        wineService.save(Wine.builder().name("Etna Bianco").quantity(1).build());

        List<Wine> result = wineService.search(
                SearchCriteria.builder().names(Set.of("  Etna Bianco  ")).build(), null);

        assertThat(result).extracting(Wine::name).containsExactly("Etna Bianco");
    }
}
