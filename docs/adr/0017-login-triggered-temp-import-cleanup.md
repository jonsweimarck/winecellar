# 0017: Övergivna temp-importmappar städas vid inloggning, inte via ett schema

## Status

Accepted (2026-07-28)

## Context

WINE-24 (se [0014](0014-web-based-excel-import-export.md)) sparar en
påbörjad imports uppladdade filer i en temporär mapp på disk mellan
torrkörningen och commit-steget - bara en sökväg hålls i HTTP-sessionen,
för att inte belasta serverminnet. Mappen städas idag bara om
användaren faktiskt fullföljer importen (commit). En användare som
aldrig bekräftar (stänger fliken, sessionen går ut) lämnar mappen
kvarglömd på disk för alltid - ingen mekanism städar bort den.

Två sätt att trigga en städning diskuterades:

1. En schemalagd bakgrundsuppgift (t.ex. `@Scheduled`) som periodiskt
   svepar igenom och tar bort gamla mappar, oavsett vad som händer i
   övrigt i appen.
2. Piggyback på ett existerande, redan återkommande händelseflöde i
   appen - en inloggning - och låta DEN trigga samma svep.

## Decision

Städningen triggas av en lyckad inloggning (via Spring Securitys
publicerade autentiseringshändelse), inte av en egen schemalagd
bakgrundsuppgift.

Motivering: appen har redan ett naturligt, återkommande tillfälle där
kod exekveras utan användarens aktiva medverkan i just det ögonblicket
- en inloggning. Att haka på det tillfället undviker att introducera en
helt ny sorts mekanism (bakgrundstråd med egen livscykel, eget
schema/intervall att underhålla) för ett lågriskproblem som bara
handlar om diskstädning, inte korrekthet. Mapparna är inte knutna till
en specifik användare, så en städning som triggas av VILKEN SOM HELST
lyckad inloggning är giltig - den behöver ändå svepa igenom samtliga
gamla mappar, inte bara den inloggade användarens egna.

## Consequences

- **Självregistrering (WINE-11) loggar in automatiskt utan att gå
  via samma inloggningsflöde** som en vanlig formulärinloggning - den
  händelsen triggar alltså INTE en städning. En nyregistrerad
  användares första session missar därmed svepet, men täcks in av
  nästa VANLIGA inloggning (av vem som helst).
- **Ingen städning sker om ingen loggar in** under en längre period -
  till skillnad från ett schema, som skulle svepa oavsett
  inloggningsaktivitet. Accepterat eftersom detta är ett
  lågtrafikprojekt där konsekvensen (kvarglömda mappar en stund till)
  bara kostar disk, aldrig korrekthet eller säkerhet.
- Ingen ny bakgrundsmekanism (schemaläggning, egen tråd) introduceras i
  kodbasen - städlogiken körs synkront som en del av ett redan
  existerande request (inloggningen), återanvänder appens vanliga
  request-tråd.
- Om städningsbehovet visar sig otillräckligt i praktiken (t.ex. långa
  perioder utan inloggningar i produktion, eller fler orsaker till
  övergivna mappar än förväntat) är en schemalagd bakgrundsuppgift ett
  rimligt nästa steg - inget i det här beslutet stänger den vägen, det
  är bara inte motiverat att bygga den i förväg.
