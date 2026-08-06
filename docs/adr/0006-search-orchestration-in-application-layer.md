# 0006: Filtrering/sökning/sortering orkestreras i applikationslagret, inte i controllern

## Status

Accepted (2026-07-21)

## Context

Vinlistan behövde sortering, filtrering (vintyp, land/region/underregion)
och fritextsökning. Dessa tre funktioner kunde ha implementerats nära
webblagret, där de inkommande valen redan finns tillgängliga, eller i
applikationslagret.

Projektets Gherkin-/Cucumber-scenarier testar redan mot
applikationslagret, inte mot HTTP. Hade orkestreringen legat i
webblagret hade scenarier om sortering/filtrering/sökning inte haft
något naturligt ställe att anropa in på utan att gå via en simulerad
HTTP-förfrågan, vilket hade suddat ut den gräns projektet redan håller
isär.

## Decision

Webblagret tolkar bara inkommande val till typade värden -
applikationslagret gör själva jobbet: väljer en baslista (fritextsökt
om en sökterm finns, annars alla), filtrerar den mot de valda
facetterna, och sorterar sist.

Sökkriterierna samlas i ett enda, byggbart värdeobjekt med rimliga
standardvärden, så anropsplatser bara anger det de faktiskt bryr sig
om. Facetterna kombineras med OCH sinsemellan (vintyp OCH land OCH
region OCH underregion, om satta), ELLER inom en facett (t.ex. Rött
eller Vitt). Land/region/underregion-trädet för filterpanelen härleds
fräscht från samtliga viner vid varje anrop - statiska facetter,
alltid obegränsade av det aktiva filtret.

## Consequences

- Webblagrets egna tester är opåverkade av var logiken bor, eftersom
  de redan stubbar bort hela applikationslagret.
- Sortering byggdes före filtrering och sökning (i den ordningen)
  eftersom sortering inte krävde någon databasändring, vilket lät hela
  mönstret - inkommande val, verktygsrad, orkestrering i
  applikationslagret - etableras innan de mer komplexa bitarna byggdes
  ovanpå det.
- Sorteringen appliceras alltid sist och skriver därmed över
  fritextsökningens relevansrankning (se
  [0007](0007-fulltext-search-tsvector.md)) om användaren inte
  uttryckligen valt en annan sortering - ingen separat
  "Relevans"-sortering är byggd.
- Chips som visar aktivt filter/sökning ligger medvetet i webblagret,
  inte i applikationslagret - ren presentationslogik utan
  Gherkin-relevans.
