# 0002: Responsiv vinlista via två layouter bakom en CSS-brytpunkt

## Status

Accepted (2026-07-12, tabellvyn omdesignad 2026-07-19/20)

## Context

`winecellar` ska vara lika användbart på mobil som på dator - i
motsats till `roombooking`, som aldrig behövde verifiera visuellt
beteende (bara innehållet i ett fragment av sidan), är just detta den
svåra delen av det här projektet. Domänlagret är tunt (se
[0001](0001-thin-domain-layer.md)); komplexiteten ligger i
presentationen, inte i logiken.

## Decision

Vinlistan renderar två layouter av samma data i samma sidmall och
växlar mellan dem med en CSS-brytpunkt: breda kort på desktop (fyra
kolumner, alla fält synliga direkt, inget infällt) och smala kort med
en infälld "Detaljer"-sektion på mobil, utan att kräva JavaScript.
Fälten som är gemensamma för båda vyerna delas via en gemensam
mall-byggsten istället för att dupliceras.

Ett eget, webbläsarbaserat testlager verifierar att rätt layout
faktiskt är synlig vid respektive brytpunkt, eftersom de vanliga
kontrollertesterna inte kör CSS och därför inte kan bevisa att
växlingen fungerar.

## Consequences

- Två layouter måste hållas i synk manuellt när ett fält läggs till
  eller tas bort - ingen delad layoutmotor, bara delad data.
- Ett webbläsarbaserat testverktyg (med en egen testdatabas) krävs
  som testberoende utöver de vanliga kontrollertesterna - ett tyngre
  testlager än `roombooking` behövde.
- Testerna måste spegla en riktig telefons faktiska
  renderingsbeteende, inte bara en smal skärm - en äkta mobil
  webbläsare beter sig annorlunda än att bara krympa ett fönster, och
  testuppsättningen måste medvetet efterlikna det för att inte missa
  buggar som bara syns på en riktig telefon.
- Tabellvyn ersattes helt av en bredare kortvariant (2026-07-19/20)
  utan infälld Detaljer på desktop - alla fält visas direkt där,
  eftersom skärmutrymmet finns. Sidans maxbredd och brytpunkten mellan
  vyerna justerades för att rymma fasta betygskolumner som inte kan
  krympa.
