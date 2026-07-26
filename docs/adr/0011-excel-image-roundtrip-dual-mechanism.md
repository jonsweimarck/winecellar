# 0011: Excel-bildrundtripp via två oberoende mekanismer

## Status

Delvis Superseded (2026-07-26, WINE-20/WINE-23, se
[0014](0014-web-based-excel-import-export.md)/
[0015](0015-bulk-import-images-lossy-jpeg.md)) - mekanism 2 nedan
(`WINECELLAR_LOCAL_IMAGE_FOLDER`, den delade lokala mappen) fanns bara
i det nu helt borttagna CLI-verktyget (`tools/import-excel/`) och
existerar inte längre. Ersatt av en webbaserad zip-nedladdning/
-uppladdning (`GET /export/bilder.zip`, `POST /import`) - som
dessutom, till skillnad från den gamla mekanismen, INTE är bit-exakt
för bulkimportens del (klientsidans bildkomprimering, se ADR 0015).
Mekanism 1 (ankrad xlsx-`Picture`) lever kvar OFÖRÄNDRAD i
`WineRowWriter`/`ExportController` - den delen av beslutet nedan
gäller fortfarande som skrivet.

Accepted (2026-07-22)

## Context

Excel-exporten (se [0010](0010-excel-tool-standalone-module.md)) skulle
även omfatta vinernas etikettbilder, med målet att en efterföljande
återimport skulle återskapa bilderna fullständigt - inte bara
textfälten.

Källfilens ursprungliga bildformat ("bild i cell", Excels inbäddade rich
data) läses medvetet inte av `WineRowParser` - att extrahera den robust
är inte värt komplexiteten för ett engångsskript, och det formatet är
dessutom mycket enklare att SKRIVA än att LÄSA med Apache POI.

## Decision

Bilder exporteras på två oberoende sätt:

1. Som en vanlig ankrad POI-`Picture` i xlsx-filens "Bild"-kolumn - bara
   en visuell bekvämlighet för att bläddra bilder direkt i Excel. Stödda
   format: JPEG/PNG/GIF (OOXML har inget bildformat för WEBP, och POI
   ingen motsvarande konstant) - en obekant MIME-typ hoppas över med en
   varning istället för att krascha.
2. Som en riktig bildfil i en delad mapp
   (`WINECELLAR_LOCAL_IMAGE_FOLDER`, **samma** miljövariabel som
   `ImageMatcher` redan använde för att koppla bilder vid import), döpt
   exakt som vinets namn. **Det är den här mekanismen, inte den ankrade
   xlsx-bilden, som gör rundtrippen fullständig** - alla format
   `ImageMatcher` känner igen (inklusive webp) skrivs hit, och
   `ImportExcel` läser tillbaka från samma mapp vid en efterföljande
   import.

## Consequences

- Full rundtripp kräver att samma `WINECELLAR_LOCAL_IMAGE_FOLDER` pekas
  ut vid både export och en efterföljande återimport - annars kommer
  textdatan tillbaka men inte bilderna.
- Miljövariabeln döptes om från `WINECELLAR_IMPORT_IMAGE_FOLDER` till
  `WINECELLAR_LOCAL_IMAGE_FOLDER` för att spegla att den nu delas åt
  båda hållen, inte bara vid import.
- Denna bildrundtripp krävde i sin tur att [0005](0005-only-name-required.md)s
  regel tillämpades konsekvent även på importsidan (`WineRowParser`) -
  annars hade ett namn-bara vin med bild fortfarande hoppats över vid
  återimport, trots att både text och bild fanns tillgängliga.
- Ingen egen automatiserad test täcker bildmappsskrivningen
  (`ExportExcel.skrivBildfiler`) - verifierad manuellt, i linje med
  modulens övriga JDBC-integration (se [0010](0010-excel-tool-standalone-module.md)).
