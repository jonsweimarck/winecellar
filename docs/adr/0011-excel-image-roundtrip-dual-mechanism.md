# 0011: Excel-bildrundtripp via två oberoende mekanismer

## Status

Deprecated (2026-07-27, WINE-32). Båda mekanismerna beslutet nedan
beskriver är nu borta:

- Den delade lokala bildmappen försvann redan 2026-07-26 (WINE-20/
  WINE-23, se [0014](0014-web-based-excel-import-export.md)/
  [0015](0015-bulk-import-images-lossy-jpeg.md)) - ersatt av en
  webbaserad nedladdning/uppladdning av en samlad bildarkivfil, som
  till skillnad från den gamla mekanismen INTE är bit-exakt för
  bulkimportens del (klientsidans bildkomprimering, se ADR 0015).
- Den ankrade bilden inne i själva kalkylbladet togs bort 2026-07-27
  (WINE-32): den var alltid bara en visuell bekvämlighet för att
  bläddra bilder direkt i Excel, aldrig en del av den faktiska
  rundtrippen - den lästes aldrig tillbaka vid import, och sedan den
  delade bildmappen försvann fanns ingen kvarvarande anledning att
  behålla en kalkylbladsspecifik bildväg som ingen kod längre läste.
  Kolumnen är nu helt borttagen ur layouten, inte bara tömd.

Ingen efterträdande ADR - det här är en ren avveckling, inte en
ersättning med ett nytt beslut. Innehållet nedan är historiskt: det
beskriver ett beslut som en gång gällde, inte hur koden fungerar idag.

Accepted (2026-07-22)

## Context

Excel-exporten (se [0010](0010-excel-tool-standalone-module.md)) skulle
även omfatta vinernas etikettbilder, med målet att en efterföljande
återimport skulle återskapa bilderna fullständigt - inte bara
textfälten.

Källfilens ursprungliga bildformat (Excels inbäddade "bild i cell")
lästes medvetet aldrig - att extrahera den robust var inte värt
komplexiteten för ett engångsskript, och det formatet är dessutom
mycket enklare att SKRIVA än att LÄSA.

## Decision

Bilder exporterades på två oberoende sätt:

1. Som en vanlig, ankrad bild i kalkylbladets bild-kolumn - bara en
   visuell bekvämlighet för att bläddra bilder direkt i Excel. Ett
   fåtal vanliga bildformat stöddes; ett obekant format hoppades över
   med en varning istället för att krascha.
2. Som en riktig bildfil i en delad lokal mapp, döpt exakt som vinets
   namn. **Det är den här mekanismen, inte den ankrade bilden i
   kalkylbladet, som gjorde rundtrippen fullständig** - alla format
   som kändes igen vid import skrevs hit, och en efterföljande import
   läste tillbaka från samma mapp.

## Consequences

- Full rundtripp krävde att samma delade mapp pekades ut vid både
  export och en efterföljande återimport - annars kom textdatan
  tillbaka men inte bilderna.
- Denna bildrundtripp krävde i sin tur att
  [0005](0005-only-name-required.md)s regel (bara namnet obligatoriskt)
  tillämpades konsekvent även på importsidan - annars hade ett
  namn-bara vin med bild fortfarande hoppats över vid återimport,
  trots att både text och bild fanns tillgängliga.
- Ingen egen automatiserad test täckte bildmappsskrivningen -
  verifierad manuellt, i linje med verktygets övriga databasintegration
  (se [0010](0010-excel-tool-standalone-module.md)).
