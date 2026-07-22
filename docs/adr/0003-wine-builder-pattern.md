# 0003: `Wine` byggs med Builder-mönster, inte positionell konstruktor

## Status

Accepted (2026-07-17)

## Context

`Wine` växte från sju fält till 23 i takt med att Excel-importen (se
[0010](0010-excel-tool-standalone-module.md)) krävde att hela
`Vinlista.xlsx`s kolumnuppsättning fick plats i domänmodellen. En
positionell record-konstruktor med 23 parametrar är oläsbar på
anropsplatsen och lätt att kasta om av misstag (två intilliggande
`String`-fält i fel ordning ger inget kompilatorfel).

## Decision

`Wine` är fortfarande en Java `record`, men all konstruktion sker via
`Wine.builder()...build()` (och `vin.toBuilder()...build()` för
with-liknande ändringar), inte `new Wine(...)` direkt. Motsvarande
mönster tillämpas på `WineEntity` (no-arg-konstruktor + paketprivata
settrar istället för en lika lång positionell konstruktor).

## Consequences

- Varje fält sätts med namn på anropsplatsen (`.wineType(...)`,
  `.vintage(...)` osv.), vilket gör både produktionskod och tester
  läsbara trots antalet fält.
- Ett nytt fält kräver en ny builder-metod, inte en ändring av alla
  positionella anropsplatser i hela kodbasen.
- `new Wine(...)` används medvetet ingenstans - konsekvent mönster,
  inte en blandning av båda stilarna.
