# 0003: Domänobjekt byggs med ett namngivet konstruktionsmönster, inte en positionell konstruktor

## Status

Accepted (2026-07-17)

## Context

Vinets datamodell växte från sju fält till 23 i takt med att
Excel-importen (se [0010](0010-excel-tool-standalone-module.md))
krävde att hela källfilens kolumnuppsättning fick plats i
domänmodellen. En konstruktor med så många positionella parametrar är
oläsbar på anropsplatsen och lätt att kasta om av misstag - två fält
av samma typ i fel ordning ger inget fel vid kompilering.

## Decision

Domänobjektet är fortfarande en oföränderlig datatyp, men all
konstruktion sker via ett namngivet, stegvis byggmönster (Builder) -
varje fält sätts med sitt namn på anropsplatsen, inte via en lång
parameterlista i en bestämd ordning. Ändring av ett redan byggt objekt
sker genom att utgå från det befintliga objektets värden och bara
ändra det som faktiskt ska ändras. Motsvarande mönster tillämpas i
persistenslagrets motsvarande representation.

## Consequences

- Varje fält namnges på anropsplatsen, vilket gör både produktionskod
  och tester läsbara trots antalet fält.
- Ett nytt fält kräver en ny byggmetod, inte en ändring av alla
  positionella anropsplatser i hela kodbasen.
- Mönstret tillämpas konsekvent - en blandning av byggmönster och
  direkt konstruktion undviks medvetet.
