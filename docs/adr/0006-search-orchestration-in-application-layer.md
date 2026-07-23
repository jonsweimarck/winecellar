# 0006: Filtrering/sökning/sortering orkestreras i `WineService`, inte i controllern

## Status

Accepted (2026-07-21)

## Context

Vinlistan behövde sortering, filtrering (vintyp, land/region/underregion)
och fritextsökning. Dessa tre funktioner kunde ha implementerats i
`WineController` (nära HTTP-lagret, där queryparametrarna redan finns)
eller i `WineService` (applikationslagret).

Projektets Gherkin-/Cucumber-scenarier testar redan mot applikationslagret,
inte mot HTTP (se `CucumberSpringConfiguration`). Hade orkestreringen legat
i controllern hade scenarier om sortering/filtrering/sökning inte haft
något naturligt ställe att anropa in på utan att gå via `MockMvc`/riktig
HTTP, vilket hade suddat ut den gräns projektet redan håller isär.

## Decision

`WineController` tolkar bara råa queryparametrar till typade värden
(`SortField`, `SortDirection`, m.fl.) - `WineService.search(
SearchCriteria)` gör själva jobbet: väljer baslista (fritextsökning via
`WineRepository.search(...)` om en sökterm finns, annars `findAll()`),
filtrerar den mot facetterna, och sorterar sist.

`SearchCriteria` är en `Builder`-baserad record (se
[0003](0003-wine-builder-pattern.md) för samma resonemang) med
defaultvärden, så anropsplatser bara sätter det de faktiskt bryr sig om.
Facetterna kombineras med OCH sinsemellan (vintyp OCH land OCH region OCH
underregion, om satta), ELLER inom en facett (t.ex. Rött eller Vitt).
Land/region/underregion-trädet för filterpanelen (`OriginNode`,
`WineService.originTree()`) härleds fräscht från samtliga viner vid
varje anrop - statiska facetter, alltid obegränsade av det aktiva
filtret.

(Klassnamnen ovan döptes om från svenska till engelska 2026-07-23,
WINE-4 - se CLAUDE.md:s "Namngivning" - men den arkitektoniska
uppdelningen de beskriver är oförändrad.)

## Consequences

- `WineControllerTest` (`@WebMvcTest` + `@MockBean WineService`) är
  opåverkad av var logiken bor - den mockar redan bort hela
  `WineService`.
- Sortering byggdes före filtrering och sökning (i den ordningen) eftersom
  sortering inte krävde någon databasändring, vilket lät hela mönstret -
  queryparametrar, htmx-verktygsrad, orkestrering i `WineService` -
  etableras innan de mer komplexa bitarna byggdes ovanpå det.
- Sorteringen appliceras alltid sist och skriver därmed över
  fritextsökningens relevansrankning (`ts_rank`, se
  [0007](0007-fulltext-search-tsvector.md)) om användaren inte
  uttryckligen valt en annan sortering - ingen separat
  "Relevans"-sortering är byggd.
- Chips som visar aktivt filter/sökning (`WineController.SearchView`) ligger
  medvetet i webblagret, inte i `WineService` - ren presentationslogik
  utan Gherkin-relevans.
