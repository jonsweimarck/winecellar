# 0016: Antal flaskor blir obligatoriskt, precis som namnet

## Status

Accepted (2026-07-26) - Supersedes [0005](0005-only-name-required.md).

## Context

WINE-28 (bugg): en bulkimport där en dubblett skulle öka det befintliga
vinets antal gav fel sluttal (2+2 blev 3, inte 4). Grundorsaken var att
`WineService.increaseQuantity(...)` (byggd för WINE-6:s manuella
"öka med en flaska"-knapp i dubblettvarningen) alltid lade till exakt 1,
oavsett hur många flaskor den importerade raden faktiskt angav -
`ImportController` återanvände samma metod rakt av.

Den uppenbara fixen (låt bulkimporten läsa av radens eget `quantity` och
använda DET som tillägg) kräver att veta VILKET tal som ska adderas när
raden inte har något värde alls (`quantity` är nullable sedan
[0005](0005-only-name-required.md)). Att gissa ett värde (t.ex. falla
tillbaka på 1) bygger bara en ny, tystare variant av samma
"vi vet inte hur många flaskor det faktiskt handlar om"-problem.

## Decision

`quantity` ("antal flaskor") blir obligatorisk data, i samma
bemärkelse som `name` redan är det - `Wine` har nu bara två
obligatoriska fält istället för ett.

- **Domänen:** `Wine.quantity` går från nullable `Integer` till
  primitiv `int` (samma resonemang som ledde till att den blev
  `Integer` i [0005](0005-only-name-required.md), fast i motsatt
  riktning - en primitiv kan inte representera "inget värde ännu", och
  det är exakt vad vi inte längre vill tillåta för det här fältet).
  `vintage` och alla andra fält som blev nullable i 0005 förblir
  oförändrade - det här beslutet gäller bara `quantity`.
- **Webbformuläret** löser kravet med ett förifyllt standardvärde
  (`antal = 1`) när "Lägg till vin"-formuläret öppnas tomt, inte en
  hård valideringsspärr - samma "snabbt kunna logga ett vin"-princip
  som motiverade 0005 ursprungligen. En blank inskickning (om
  standardvärdet ändå raderas) faller tillbaka till 1 server-side,
  istället för att avvisas.
- **Excel-import** kräver att cellen faktiskt är ifylld - en rad utan
  antal hoppas över på exakt samma sätt som en rad utan namn redan gör
  (`WineRowParser.RowMissingRequiredFieldsException`). Ingen gissning
  här: om en rad inte anger hur många flaskor det gäller finns inget
  säkert sätt att härleda det, och att importera en okänd mängd är värre
  än att hoppa över raden och låta användaren rätta den.
- **Databasen:** `quantity` blir `NOT NULL`, satt direkt i `schema.sql`
  (inte via `@Column(nullable = false)` i `WineEntity` - se
  `WineEntity`s befintliga mönster för `owner_id`, samma skäl:
  Hibernates `ddl-auto: update` har visat sig opålitligt för den här
  sortens `ALTER` under det här projektets historia, se CLAUDE.md).
  Kräver att befintliga `NULL`-rader i produktionsdatabasen backfylls
  till 1 INNAN `SET NOT NULL` körs - en engångsmigrering, samma mönster
  som WINE-17s ägartilldelning.

## Consequences

- Reverserar en specifik del av [0005](0005-only-name-required.md) -
  `name` och `quantity` är nu de två obligatoriska fälten, inte bara
  `name`. 0005 markeras Superseded av den här ADR:n.
- `WineService.increaseQuantityBy(WineId, UserId, int)` ersätter behovet
  av att gissa ett tillägg vid bulkimport - `ImportController` kan nu
  alltid skicka in den importerade radens eget `quantity` rakt av, utan
  fallback. `WineService.increaseQuantity(WineId, UserId)` (WINE-6:s
  "+1 flaska"-knapp) blir en tunn wrapper som anropar den nya metoden
  med `amount=1` - oförändrat beteende för den befintliga
  dubblettvarnings-UI:n.
- En engångsmigrering måste köras mot produktionsdatabasen FÖRE denna
  deploy (backfylla `NULL`-rader till 1) - annars kraschar `schema.sql`s
  `SET NOT NULL`-sats vid nästa appstart, exakt samma fälla som
  `owner_id`- och `search_vector`-sagorna redan dokumenterat i CLAUDE.md.
- Ett vin byggt direkt via `Wine.builder()...build()` utan att
  `.quantity(...)` anropas får Javas vanliga standardvärde för `int`
  (0), inte `null` - påverkar några enhetstester/Cucumber-scenarier som
  tidigare kontrollerade att ett minimalt vin hade `quantity() == null`.
- `vintage` och övriga fält som redan var nullable via 0005 förblir
  helt opåverkade - det här är en punktinsats för `quantity` specifikt,
  inte en generell återgång till "fler fält obligatoriska".
