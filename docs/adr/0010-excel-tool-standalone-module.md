# 0010: Excel-import/export som ett fristående, separat verktyg

## Status

Superseded av [0014](0014-web-based-excel-import-export.md)
(2026-07-25, WINE-20) - det fristående verktyget är borttaget,
importen/exporten blev en webbfunktion i huvudappen istället. Kvar
nedan som historik för varför den ursprungliga modellen såg ut som den
gjorde.

Accepted (2026-07-17)

## Context

Den befintliga vinsamlingen fanns i en Excel-fil som skulle importeras
en gång, och en motsvarande exportmöjlighet önskades senare för
redigering/backup. Biblioteket för att läsa/skriva Excel-filer är ett
tungt beroende som bara behövs för dessa engångs-/verktygskörningar -
inte för den körande webbapplikationen.

## Decision

Import-/exportverktyget byggdes som en helt fristående modul, separat
byggd från huvudapplikationen, så att dess beroenden inte följde med i
den körande appen. Modulen återanvände huvudapplikationens egna
domänobjekt (vintyp, betyg) istället för att duplicera regler och
mappningslogik, men skrev/läste direkt mot databasen - ett fristående
skript som kördes manuellt mot en redan existerande databas, inte via
applikationens vanliga tjänstelager.

## Consequences

- Huvudapplikationens byggkonfiguration behövde en mindre justering
  för att kunna paketeras på ett sätt som gick att bero på som ett
  vanligt bibliotek, utan att påverka hur den vanliga driftmiljön
  startar appen.
- Ingen Gherkin-täckning för verktyget - verifiering av det fulla
  import-/exportflödet skedde manuellt mot en riktig databas, medan
  radmappningslogiken hade egna, vanliga enhetstester.
- Kolumnlayouten delades mellan läsningen och skrivningen via en
  gemensam källa till sanning istället för att duplicera kolumnindexen
  i två klasser.
- Verktygen kördes lokalt och pratade med produktionsdatabasen över
  nätverket när de riktades mot produktion - driftmiljön har inget
  sätt att köra ett sådant verktyg *på plats*, men det behövdes inte
  heller.
