# 0008: Filterchips är vanliga länkar, inte htmx

## Status

Accepted (2026-07-22)

## Context

Vinlistan visar en chip per aktivt filter-/sökvärde (vintyp, land,
region, underregion, sökterm), med en borttagningslänk som tar bort
bara det värdet. Resten av appens dynamiska interaktioner (sortering,
filtrering, borttagning av vin) går via htmx och swappar bara
`#vinlista`-fragmentet.

En chip-borttagning måste däremot uppdatera **hela verktygsraden**
(kryssrutor, sökfält) för att förbli i synk med den nya URL:en, inte
bara listan.

## Decision

Chip-länkarna är vanliga `<a href>`, inte htmx-drivna. `WineController`
bygger om URL:en minus det borttagna värdet (`Sökvy.urlUtan(facett,
värde)`, `UriComponentsBuilder`) och en vanlig sidladdning garanterar
att både verktygsrad och lista är synkade.

## Consequences

- En chip-borttagning ger en full sidladdning, inte en snabb
  htmx-swap - en medveten avvägning för korrekthet (hela verktygsraden
  måste reflektera det nya tillståndet), inte en prestandaoptimering.
- Byggd i `WineController`, inte `WineService` - ren presentationslogik
  utan Gherkin-relevans, i linje med [0006](0006-search-orchestration-in-application-layer.md).
