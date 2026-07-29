# 0014: Webbaserad Excel-import/export, scopead till inloggad användare

## Status

Accepted (2026-07-25) - liksom [0013](0013-multi-user-accounts.md) är
beslutet nedskrivet innan implementationen påbörjas, eftersom
omställningen spänner över flera separata stories (Fas 2 i YouTrack,
WINE-projektet). Implementationen landade 2026-07-26 (WINE-20 till
WINE-26) - se CLAUDE.md för byggloggen och
[0015](0015-bulk-import-images-lossy-jpeg.md) för en avvägning som
upptäcktes först under byggandet (bulkimportens bilder rundtrippar
inte bit-exakt).

## Context

[0013](0013-multi-user-accounts.md) (Fas 1, klar) bekräftade riktningen
för Fas 2 i sina punkter 6-7, utan att fastslå detaljerna: import/export
blir en webbfunktion, scopead till inloggad användare - vilket river upp
[0010](0010-excel-tool-standalone-module.md)s beslut att hålla Apache
POI utanför den deployade appen. Bilder vid import hanteras via en
mappväljare i webbläsaren (`<input type="file" webkitdirectory>`), inte
en lokal serverfilsökväg som dagens `tools/import-excel`-verktyg
(`ImageMatcher`, `WINECELLAR_LOCAL_IMAGE_FOLDER`) förutsätter.

Det ursprungliga skälet till att hålla verktyget fristående - en
engångsimport som kördes förbi autentisering, direkt via JDBC mot
`wines`-tabellen - gäller inte längre. Varje vin behöver numera en
riktig ägare (`owner_id NOT NULL`, se [0013](0013-multi-user-accounts.md)/
WINE-15), så det naturliga sättet att importera är som inloggad
användare via webben, precis som att lägga till ett vin manuellt redan
fungerar.

## Decision

1. **`tools/import-excel` pensioneras helt, inte ett parallellt
   verktyg.** `WineRowParser`/`WineRowWriter`/`ImageMatcher` flyttas in
   i huvudappen som återanvändbar kod (paket `infrastructure` eller ett
   nytt `excel`-underpaket). `DatabaseConnection` (rå JDBC rakt mot
   tabellen) tas bort - webbimporten skriver varje rad via
   `WineService.save(...)`, inte direkt SQL, för att få samma
   ägarskopning och dubblettkontroll som manuell tillägg redan har.
   Apache POI blir därmed ett riktigt runtime-beroende av den deployade
   jaren. En sidoeffekt: rotens `pom.xml`s `<classifier>exec</classifier>`
   på `spring-boot-maven-plugin` (se [0010](0010-excel-tool-standalone-module.md))
   blir överflödig när inget längre beror på artefakten som ett vanligt
   bibliotek, och kan tas bort.

2. **Dubblettkontroll återanvänder WINE-6:s identitetsbegrepp
   (namn/producent/årgång, `Wine.matchesIdentityOf`/`hasCompleteIdentity`)
   rakt av**, inte en egen importspecifik jämförelse.

3. **Import inleds med en torrkörning/förhandsgranskning, inte direkt
   commit.** Den uppladdade filen (och ev. bildmapp) parsas och
   dubblettkontrolleras mot befintliga viner UTAN att skriva något -
   användaren får se hur många rader som är rena, hur många som är
   fullständiga respektive partiella dubbletter, och hur många som
   hoppas över (saknar namn). Först efter ett uttryckligt bekräftelsesteg
   körs det faktiska importet.

4. **Dubblettstrategin är EN gemensam inställning för hela importen,
   inte per rad** - två separata val, som speglar de val som redan
   finns i formulärets dubblettvarning (WINE-6):
   - **Fullständig dubblett:** "öka antal på befintligt" (default)
     eller "hoppa över". Inget "lägg till ändå" här, precis som i
     formuläret idag - det är per definition samma vin.
   - **Partiell dubblett:** "öka antal på befintligt", "lägg till som
     nytt vin ändå", eller "hoppa över".

5. **Bilder matchas EXAKT via filnamn byggt av vinets satta fält**:
   `<producent>_<namn>_<årgång>` när alla tre finns, `<producent>_<namn>`
   när årgång saknas, `<namn>_<årgång>` när producent saknas, och bara
   `<namn>` när endast namnet är känt. Mellanslag inom producent- och
   vinnamn bevaras; endast separatorn mellan fälten är understreck.
   Detta är entydigt även när flera viner delar namn, vilket den
   tidigare namn-bara matchningen (`ImageMatcher`) inte klarade.
   **Ingen fallback till namn-bara matchning för en rad med känd
   identitet** (WINE-35) - en rad med producent och/eller årgång satt
   matchar bara sin egen, specifika stam; hittas ingen fil kopplas ingen
   bild, hellre än att råka matcha en annan, oidentifierad bildfil som
   händelsevis delar namn (det hade återinfört exakt den tvetydighet den
   här punkten är till för att lösa). En rad helt UTAN identitet (bara
   namnet känt) får redan en stam identisk med namnet, så namn-bara
   bildfiler fortsätter fungera för sådana rader precis som innan -
   bakåtkompatibelt med äldre bildfiler och med rader där bara namnet är
   känt (redan idag ett medvetet stött scenario, se
   [0005](0005-only-name-required.md)).

6. **Export är två separata nedladdningar:** en `.xlsx`-fil (scopead
   till inloggad användares egna viner, samma kolumnlayout som idag) och
   en `.zip`-nedladdning med bildfiler namngivna enligt samma
   fältbaserade konvention som import förväntar sig (se punkt 5) - det
   är zip-filens bilder, inte xlsx-filens inbäddade `Picture`-objekt
   (se [0011](0011-excel-image-roundtrip-dual-mechanism.md) punkt 1, som
   kvarstår oförändrad som en ren visuell bekvämlighet i Excel), som ger
   en fullständig rundtripp: användaren väljer samma uppackade zip-mapp
   som bildmapp vid en efterföljande import.

## Consequences

- Apache POI (och dess transitiva beroenden) blir ett riktigt
  runtime-beroende av den deployade appen - en medveten avvikelse från
  [0010](0010-excel-tool-standalone-module.md)s ursprungliga motivering,
  eftersom funktionen nu är en riktig, användarvänd webbfunktion och
  inte längre ett utvecklarverktyg.
- [0010](0010-excel-tool-standalone-module.md) markeras Superseded av
  den här ADR:n när `tools/import-excel` faktiskt tas bort (sista
  implementationssteget, inte redan nu).
- [0011](0011-excel-image-roundtrip-dual-mechanism.md) förblir Accepted
  för den delen som fortfarande gäller (xlsx-inbäddningen), men dess
  huvudmekanism för fullständig rundtripp (den delade lokala mappen,
  `WINECELLAR_LOCAL_IMAGE_FOLDER`) ersätts av zip-nedladdning/-uppladdning
  - noterat i den ADR:n nu när implementationen (WINE-23) har landat.
- Ny UI-yta behövs: en sida (länkad från vinlistan, t.ex.
  "Importera/exportera") med tre delar - ladda upp för torrkörning,
  bekräfta/välj dubblettstrategi, och två exportknappar.
- Import går via `WineService.save(...)` per rad, inte en bulk-SQL-sats
  - långsammare för stora filer än den gamla CLI-vägen, men samlingens
  förväntade storlek (tiotals-hundratals rader) gör det försumbart, och
  vinsten (konsekvent ägarskopning/dubblettkontroll/validering) väger
  klart tyngre.
- Ingen dedikerad radbegränsning bestäms här. Multipart-gränserna
  (`spring.servlet.multipart.max-file-size`/`max-request-size`)
  visade sig dock behöva höjas under implementationen (5MB räckte inte
  för en bulkimports bildmapp) - löst med en kombination av höjda
  gränser OCH klientsidans bildkomprimering, se
  [0015](0015-bulk-import-images-lossy-jpeg.md) för den fullständiga
  avvägningen och varför den senare blev nödvändig.
- Detaljerad story-nedbrytning och byggordning spåras i YouTrack
  (WINE-projektet, Fas 2), inte här - den här ADR:n fastslår vägvalen,
  inte arbetsordningen.
