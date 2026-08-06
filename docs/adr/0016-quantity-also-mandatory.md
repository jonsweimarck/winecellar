# 0016: Antal flaskor blir obligatoriskt, precis som namnet

## Status

Accepted (2026-07-26) - Supersedes [0005](0005-only-name-required.md).

## Context

En bugg upptäcktes i bulkimportens hantering av dubbletter: när en
importerad rad matchade ett befintligt vin och antalet flaskor skulle
ökas, blev sluttalet fel eftersom importen alltid antog att exakt en
flaska skulle läggas till, oavsett vad den importerade raden faktiskt
angav.

Den uppenbara fixen - låt bulkimporten läsa av radens eget angivna
antal och använda det som tillägg - kräver att veta vilket tal som ska
adderas när raden inte anger något antal alls, eftersom antalet
flaskor tidigare fick lämnas tomt (se
[0005](0005-only-name-required.md)). Att gissa ett värde (t.ex. falla
tillbaka på en flaska) bygger bara en ny, tystare variant av samma
"vi vet inte hur många flaskor det faktiskt handlar om"-problem.

## Decision

Antalet flaskor blir obligatorisk data, i samma bemärkelse som namnet
redan är det - vinet har nu bara två obligatoriska fält istället för
ett.

- **Webbformuläret** löser kravet med ett förifyllt standardvärde (en
  flaska) när "Lägg till vin"-formuläret öppnas tomt, inte en hård
  valideringsspärr - samma "snabbt kunna logga ett vin"-princip som
  motiverade [0005](0005-only-name-required.md) ursprungligen. En
  blank inskickning (om standardvärdet ändå raderas) faller tillbaka
  till en flaska.
- **Excel-import** kräver att antalet faktiskt är ifyllt - en rad utan
  antal hoppas över på exakt samma sätt som en rad utan namn redan
  gör. Ingen gissning här: om en rad inte anger hur många flaskor det
  gäller finns inget säkert sätt att härleda det, och att importera en
  okänd mängd är värre än att hoppa över raden och låta användaren
  rätta den.
- **Databasen** kräver numera också ett värde för antalet, satt direkt
  i schemat - i linje med hur andra liknande skärpningar av
  databasbegränsningar gjorts i det här projektet, eftersom den
  vanliga schemamigreringsmekanismen har visat sig opålitlig för den
  sortens ändring (se `docs/devlog.md`). Kräver att befintliga rader
  utan värde i produktionsdatabasen backfylls till en flaska INNAN
  begränsningen skärps - en engångsmigrering, samma mönster som
  tidigare engångsmigreringar i projektet.

## Consequences

- Reverserar en specifik del av [0005](0005-only-name-required.md) -
  namn och antal är nu de två obligatoriska fälten, inte bara namn.
  0005 markeras Superseded av den här ADR:n.
- Bulkimporten kan nu alltid använda den importerade radens eget
  angivna antal som tillägg vid en dubblett, utan att behöva gissa ett
  fallback-värde. Den befintliga "öka med en flaska"-knappen i
  formulärets dubblettvarning är opåverkad i beteende.
- En engångsmigrering måste köras mot produktionsdatabasen FÖRE denna
  deploy (backfylla obefintliga värden till en flaska) - annars
  kraschar schemat vid nästa appstart, samma sorts fälla som andra
  liknande databasändringar redan dokumenterat (se `docs/devlog.md`).
- Ett vin som byggs utan att antalet uttryckligen anges får nu talet
  noll som standardvärde, inte "inget värde" - påverkar några
  enhetstester/Cucumber-scenarier som tidigare kontrollerade att ett
  minimalt vin saknade ett angivet antal helt.
- Övriga fält som blev valfria via [0005](0005-only-name-required.md)
  förblir helt opåverkade - det här är en punktinsats för antalet
  flaskor specifikt, inte en generell återgång till "fler fält
  obligatoriska".
