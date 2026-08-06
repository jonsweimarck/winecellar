# 0014: Webbaserad Excel-import/export, scopead till inloggad användare

## Status

Accepted (2026-07-25) - liksom [0013](0013-multi-user-accounts.md) är
beslutet nedskrivet innan implementationen påbörjas, eftersom
omställningen spänner över flera separata stories (Fas 2 i YouTrack,
WINE-projektet). Implementationen landade 2026-07-26 (WINE-20 till
WINE-26) - se `docs/devlog.md` för byggloggen och
[0015](0015-bulk-import-images-lossy-jpeg.md) för en avvägning som
upptäcktes först under byggandet (bulkimportens bilder rundtrippar
inte bit-exakt).

## Context

[0013](0013-multi-user-accounts.md) (Fas 1, klar) bekräftade riktningen
för Fas 2 i sina punkter 6-7, utan att fastslå detaljerna: import/export
blir en webbfunktion, scopead till inloggad användare - vilket river
upp [0010](0010-excel-tool-standalone-module.md)s beslut att hålla
Excel-biblioteket utanför den deployade appen. Bilder vid import
hanteras via en mappväljare i webbläsaren, inte en lokal serverfilsökväg
som det tidigare fristående verktyget förutsatte.

Det ursprungliga skälet till att hålla verktyget fristående - en
engångsimport som kördes förbi autentisering, direkt mot databasen -
gäller inte längre. Varje vin behöver numera en riktig, obligatorisk
ägare (se [0013](0013-multi-user-accounts.md)/WINE-15), så det
naturliga sättet att importera är som inloggad användare via webben,
precis som att lägga till ett vin manuellt redan fungerar.

## Decision

1. **Det fristående verktyget pensioneras helt, inte ett parallellt
   verktyg.** Den återanvändbara radläsnings-/radskrivnings-/
   bildmatchningslogiken flyttas in i huvudappen. Den direkta
   databaskopplingen tas bort - webbimporten skriver varje rad via
   applikationens vanliga tjänstelager, inte direkt mot databasen, för
   att få samma ägarskopning och dubblettkontroll som manuell tillägg
   redan har. Excel-biblioteket blir därmed ett riktigt beroende av
   den deployade appen. En sidoeffekt: en byggkonfigurationsjustering
   som bara fanns för att det fristående verktyget skulle kunna bero
   på huvudapplikationen som ett vanligt bibliotek blir överflödig och
   kan tas bort.

2. **Dubblettkontroll återanvänder WINE-6:s identitetsbegrepp
   (namn/producent/årgång) rakt av**, inte en egen importspecifik
   jämförelse.

3. **Import inleds med en torrkörning/förhandsgranskning, inte direkt
   commit.** Den uppladdade filen (och ev. bildmapp) parsas och
   dubblettkontrolleras mot befintliga viner UTAN att skriva något -
   användaren får se hur många rader som är rena, hur många som är
   fullständiga respektive partiella dubbletter, och hur många som
   hoppas över (saknar namn). Först efter ett uttryckligt
   bekräftelsesteg körs det faktiska importet.

4. **Dubblettstrategin är EN gemensam inställning för hela importen,
   inte per rad** - två separata val, som speglar de val som redan
   finns i formulärets dubblettvarning (WINE-6):
   - **Fullständig dubblett:** "öka antal på befintligt" (default)
     eller "hoppa över". Inget "lägg till ändå" här, precis som i
     formuläret idag - det är per definition samma vin.
   - **Partiell dubblett:** "öka antal på befintligt", "lägg till som
     nytt vin ändå", eller "hoppa över".

5. **Bilder matchas EXAKT via ett filnamn byggt av vinets kända
   identitetsfält** (producent, namn, årgång, i en bestämd ordning) -
   ju fler av fälten som är kända, desto mer specifikt filnamn krävs
   för en träff. Detta är entydigt även när flera viner delar namn,
   vilket den tidigare, enklare namn-baserade matchningen inte
   klarade. **Ingen fallback till den enklare namn-baserade
   matchningen för en rad med känd identitet** (WINE-35) - en rad där
   producent och/eller årgång är känt matchar bara sitt eget,
   specifika filnamn; hittas ingen fil kopplas ingen bild, hellre än
   att råka matcha en annan, oidentifierad bildfil som händelsevis
   delar namn (det hade återinfört exakt den tvetydighet den här
   punkten är till för att lösa). En rad helt UTAN känd identitet
   (bara namnet känt) fortsätter fungera med den enklare, namn-baserade
   matchningen precis som innan - bakåtkompatibelt med äldre bildfiler
   och med rader där bara namnet är känt (redan idag ett medvetet
   stött scenario, se [0005](0005-only-name-required.md)).

6. **Export är två separata nedladdningar:** en Excel-fil (scopead
   till inloggad användares egna viner, samma kolumnlayout som idag)
   och en samlad bildarkivfil med bilder namngivna enligt samma
   fältbaserade konvention som import förväntar sig (se punkt 5) - det
   är den nedladdningen, inte Excel-filens eventuellt inbäddade
   bilder (se [0011](0011-excel-image-roundtrip-dual-mechanism.md)
   punkt 1, som kvarstår oförändrad som en ren visuell bekvämlighet i
   Excel), som ger en fullständig rundtripp: användaren väljer samma
   uppackade arkiv som bildmapp vid en efterföljande import.

## Consequences

- Excel-biblioteket (och dess transitiva beroenden) blir ett riktigt
  beroende av den deployade appen - en medveten avvikelse från
  [0010](0010-excel-tool-standalone-module.md)s ursprungliga
  motivering, eftersom funktionen nu är en riktig, användarvänd
  webbfunktion och inte längre ett utvecklarverktyg.
- [0010](0010-excel-tool-standalone-module.md) markeras Superseded av
  den här ADR:n när det fristående verktyget faktiskt tas bort (sista
  implementationssteget, inte redan nu).
- [0011](0011-excel-image-roundtrip-dual-mechanism.md) förblir
  Accepted för den delen som fortfarande gäller (den inbäddade bilden
  i Excel-filen), men dess huvudmekanism för fullständig rundtripp
  (den delade lokala mappen) ersätts av den nya
  arkivnedladdningen/-uppladdningen - noterat i den ADR:n nu när
  implementationen (WINE-23) har landat.
- Ny UI-yta behövs: en sida (länkad från vinlistan) med tre delar -
  ladda upp för torrkörning, bekräfta/välj dubblettstrategi, och två
  exportknappar.
- Import går rad för rad via applikationens vanliga tjänstelager, inte
  en samlad databasoperation - långsammare för stora filer än den
  tidigare vägen, men samlingens förväntade storlek (tiotals-hundratals
  rader) gör det försumbart, och vinsten (konsekvent
  ägarskopning/dubblettkontroll/validering) väger klart tyngre.
- Ingen dedikerad radbegränsning bestäms här. Uppladdningens
  storleksgränser visade sig dock behöva höjas under implementationen
  (den ursprungliga gränsen räckte inte för en bulkimports bildmapp) -
  löst med en kombination av höjda gränser OCH klientsidans
  bildkomprimering, se [0015](0015-bulk-import-images-lossy-jpeg.md)
  för den fullständiga avvägningen och varför den senare blev
  nödvändig.
- Detaljerad story-nedbrytning och byggordning spåras i YouTrack
  (WINE-projektet, Fas 2), inte här - den här ADR:n fastslår
  vägvalen, inte arbetsordningen.
