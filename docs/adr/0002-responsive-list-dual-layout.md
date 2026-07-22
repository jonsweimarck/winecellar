# 0002: Responsiv vinlista via två layouter bakom en CSS-brytpunkt

## Status

Accepted (2026-07-12, tabellvyn omdesignad 2026-07-19/20)

## Context

`winecellar` ska vara lika användbart på mobil som på dator - i
motsats till `roombooking`, som aldrig behövde verifiera CSS-beteende
(bara ett htmx-fragments innehåll), är just detta den svåra delen av
det här projektet. Domänlagret är tunt (se
[0001](0001-thin-domain-layer.md)); komplexiteten ligger i
presentationen, inte i logiken.

## Decision

`vinkallare.html` renderar **två** layouter av samma data i samma
HTML-fragment och växlar mellan dem med en CSS media query vid 960px:
breda kort på desktop (fyra kolumner, alla fält synliga direkt, inget
infällt) och smala kort med en infälld "Detaljer"-sektion (`<details>`,
ingen JS) på mobil. Fälten som är gemensamma för båda vyerna delas via
ett Thymeleaf-fragment (`detaljfalt(vin)`) istället för att dupliceras.

Ett eget testlager (`WineListResponsiveIT`, Playwright) verifierar att
rätt layout faktiskt är synlig vid respektive brytpunkt, eftersom
`@WebMvcTest`/MockMvc inte kör CSS och därför inte kan bevisa att
växlingen fungerar.

## Consequences

- Två layouter måste hållas i synk manuellt när ett fält läggs till
  eller tas bort (t.ex. `colspan`/kolumnantal i förra tabelliterationen,
  numera fältlistan i båda kortvarianterna) - ingen delad layoutmotor,
  bara delad data via `detaljfalt`.
- Playwright krävs som testberoende utöver `@WebMvcTest`, med egen
  Testcontainers-Postgres för `WineListResponsiveIT` - ett tyngre
  testlager än `roombooking` behövde.
- En riktig `<meta name="viewport">`-tagg och Playwright-kontextens
  `isMobile(true)` (inte bara en smal `setViewportSize`) krävs för att
  testerna faktiskt ska spegla en riktig telefons renderingsbeteende -
  en smal skärm i sig räcker inte.
- Tabellvyn ersattes helt av en bredare kortvariant (2026-07-19/20)
  utan infälld Detaljer på desktop - alla fält visas direkt där,
  eftersom skärmutrymmet finns. `body`s `max-width` höjdes från 48rem
  till 70rem och brytpunkten från 640px till 960px för att rymma fasta
  betygskolumner (18rem) som inte kan krympa.
