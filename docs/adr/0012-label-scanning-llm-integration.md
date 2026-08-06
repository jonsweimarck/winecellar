# 0012: Etikettskanning via en extern LLM-tjänst, port/adapter + direkt REST-anrop

## Status

Accepted (2026-07-24)

## Context

Ambitionen (WINE-5) var att låta en användare fotografera en
vinetikett och få ett förifyllt utkast till "lägg till vin"-formuläret,
genom att skicka bilden till en extern språkmodell och be den
läsa/härleda namn, producent, årgång, land och region. Det här är
appens första beroende av en extern tjänst utöver databasen.

## Decision

**Port/adapter, samma mönster som datalagret.** Etikettolkningen
definieras som ett gränssnitt i applikationslagret, med en riktig
adapter mot den externa tjänsten i infrastrukturlagret. Till skillnad
från datalagrets enkla testdubblett (som är en legitim, om än enkel,
egen implementation) finns det ingen meningsfull produktionsanvändning
av en låtsas-språkmodell - testdubbletten för etikettolkning ligger
därför bara i testkoden, inte i den vanliga infrastrukturkoden.

En egen orkestreringstjänst i applikationslagret (inte en metod på den
befintliga vintjänsten) anropar porten, bygger ett osparat utkast av
det som kom tillbaka, och räknar ut vilka av de fem fälten som
faktiskt kunde tolkas. Samma princip som
[0006](0006-search-orchestration-in-application-layer.md)
(orkestrering hör hemma i applikationslagret), tillämpad på en annan
gräns - den befintliga vintjänsten rör bara den redan sparade
samlingen, medan etikettolkning inte har någon koppling dit alls.

**Ett tomt svar från porten betyder total misslyckad tolkning**
(nätverksfel, fel från tjänsten, eller en etikett där inget alls kunde
läsas/härledas) - skiljt från ett lyckat svar där enskilda fält råkar
sakna värde (t.ex. bara namnet gick att läsa). Adaptern slår ihop
dessa fall till "totalt misslyckande" bara om ALLA fem fälten saknar
värde, eftersom det ur användarens perspektiv är samma sak som att
tolkningen misslyckades helt.

**Anrop görs direkt mot den externa tjänstens REST-API med ett redan
tillgängligt HTTP-bibliotek, inte via tjänstens officiella
klientbibliotek.** Applikationen har redan det som behövs för att göra
och tolka ett enkelt HTTP-anrop utan något nytt beroende - samma linje
som [0010](0010-excel-tool-standalone-module.md)s resonemang att hålla
den deployade applikationen fri från beroenden som bara behövs för en
enda, smal integrationspunkt. Den externa tjänstens API är en enkel
förfrågan med en bild- och textdel; att lägga till ett helt
klientbibliotek för det vägde inte upp mot en handfull rader egen
uppbyggnad.

**Konfiguration via miljövariabler**, samma mönster som appens övriga
hemligheter: API-nyckel och valfri modellbeteckning, båda med en
ofarlig, tom lokal standard - appen startar ändå utan en riktig nyckel,
det är bara skanningsanropet som då skulle misslyckas.

**Instruktionen till språkmodellen är uttrycklig** om att namn,
producent och årgång ALDRIG får gissas eller härledas (bara läsas rakt
av det som faktiskt står), medan land och region FÅR härledas från
övrig information på etiketten - kravet kommer direkt från uppgiftens
ursprungliga beskrivning.

**Testningen är uppdelad på tre lager**, eftersom det faktiska
beteendet spänner tre olika sorters ansvar:
- Lyckad tolkning (fullständig eller delvis) verifieras mot
  applikationslagret, med en testdubblett för den externa tjänsten -
  aldrig ett riktigt anrop i tester.
- Misslyckad tolkning verifieras mot webblagret, eftersom det där
  bara är en fråga om att sidan renderas utan att krascha, ingen egen
  logik att verifiera.
- Att en redigering släcker markeringen av ett tolkat fält, och att en
  skannad bild faktiskt fyller i och markerar rätt fält i en riktig
  webbläsare, verifieras med ett webbläsarbaserat test - det förra är
  ren klientlogik utan serveranrop alls, exakt den sortens beteende
  det befintliga responsivitetstestlagret redan finns till för (se
  [0002](0002-responsive-list-dual-layout.md)). Den externa tjänsten
  mockas bort där också, av samma skäl som ovan.

## Consequences

- Appens första utgående nätverksberoende utöver databasen - ingen
  egen timeout-/återförsökshantering byggd utöver vad det använda
  HTTP-biblioteket gör som standard; ett misslyckat anrop blir bara
  ett totalt misslyckande-svar.
- Etikettskanning är bara tillgänglig vid TILLÄGG av ett nytt vin,
  inte vid redigering av ett befintligt.
- Klientsidans bildnedskalning inför uppladdning, och logiken som
  släcker en tolkningsmarkering när ett fält redigeras, är den första
  mer än triviala klientkoden i projektet - allt annat dynamiskt
  beteende i gränssnittet hade dittills varit deklarativt eller en
  enda rad.
