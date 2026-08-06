# 0008: Filterchips är vanliga länkar, inte dynamiskt uppdaterande

## Status

Accepted (2026-07-22)

## Context

Vinlistan visar en chip per aktivt filter-/sökvärde (vintyp, land,
region, underregion, sökterm), med en borttagningslänk som tar bort
bara det värdet. Resten av appens dynamiska interaktioner (sortering,
filtrering, borttagning av vin) uppdaterar bara själva listan utan en
full sidladdning.

En chip-borttagning måste däremot uppdatera **hela verktygsraden**
(kryssrutor, sökfält) för att förbli i synk med det nya urvalet, inte
bara listan.

## Decision

Chip-länkarna är vanliga länkar, inte dynamiskt uppdaterande.
Webblagret bygger om den aktuella adressen minus det borttagna
värdet, och en vanlig sidladdning garanterar att både verktygsrad och
lista är synkade.

## Consequences

- En chip-borttagning ger en full sidladdning, inte en snabb
  dellisteuppdatering - en medveten avvägning för korrekthet (hela
  verktygsraden måste reflektera det nya tillståndet), inte en
  prestandaoptimering.
- Byggd i webblagret, inte applikationslagret - ren presentationslogik
  utan Gherkin-relevans, i linje med
  [0006](0006-search-orchestration-in-application-layer.md).
