# winecellar

Webbapp för att hålla reda på en vinsamling - ersätter en Excel-fil
(`Vinlista.xlsx`, en rad per vin). Läs- och skrivbar från både dator och
mobil, deployad på Clever Cloud.

Lärprojekt i samma serie som `roombooking` (samma process: Claude Code,
Specification by Example, CI/CD). Se `docs/adr/` för arkitektur- och
designbesluten och varför de togs - den här filen beskriver bara
nuläget.

## Arkitektur

Hexagonal lagerindelning:

```
domain/          Rena domänobjekt (Wine, WineType, Rating), inga ramverksberoenden
application/     Use cases och portar (WineService, WineRepository)
infrastructure/  In-memory-testdubblett + JPA/Postgres-adapter (JpaWineRepository),
                 Excel-läsning/skrivning (infrastructure/excel/)
web/             Controller + Thymeleaf/htmx
```

Domänlagret är tunt - inga affärsregler att skydda, se
[ADR 0001](docs/adr/0001-thin-domain-layer.md). `WineService` har en
enda `save`-metod för både tillägg och redigering.

`Wine` har 23 fält och byggs alltid via `Wine.builder()...build()`
(eller `vin.toBuilder()...build()` för ändringar), aldrig via en
positionell konstruktor - se [ADR 0003](docs/adr/0003-wine-builder-pattern.md).

Alla fält är redigerbara via en egen sida (`vin-formular.html`), delad
mellan tillägg (`GET/POST /wines/nytt`) och redigering (`GET/POST
/wines/{id}/redigera`) - samma mall, formuläret postar `multipart/
form-data` med en valfri bildfil. Startsidan (`/`) visar bara listan.

Vid tillägg går det att fotografera en etikett i stället för att skriva
in fälten för hand - `POST /wines/tolka-etikett` skickar bilden till
Anthropics API och renderar om samma formulär, förifyllt med det som
kunde läsas/härledas (namn, producent, årgång, land, region), synligt
markerat tills fältet redigeras. Se
[ADR 0012](docs/adr/0012-label-scanning-llm-integration.md).

## Datamodell

Tabell `wines`:

| Kolumn | Typ | Kommentar |
|---|---|---|
| id | `bigserial` PK | |
| owner_id | `bigint` FK → `users.id`, **NOT NULL** | Se "Flera användare" nedan |
| wine_type | `text` + `CHECK`, nullable | Enum: RED, WHITE, ROSE, SPARKLING, FORTIFIED |
| country | `text`, nullable | |
| region | `text`, nullable | |
| subregion | `text`, nullable | |
| grapes | `text`, nullable | Fritext |
| producer | `text`, nullable | |
| name | `text`, **NOT NULL** | Ett av de två obligatoriska fälten |
| vintage | `smallint`, nullable | `Integer` i Java |
| image | `bytea`, nullable | Vinetikett |
| image_mime_type | `text`, nullable | T.ex. `image/jpeg` |
| purchase_date | `date`, nullable | |
| price | `numeric(10,2)`, nullable | |
| quantity | `integer`, **NOT NULL** | `int` i Java, se [ADR 0016](docs/adr/0016-quantity-also-mandatory.md) |
| purchase_reason | `text`, nullable | |
| tasting_notes | `text`, nullable | |
| own_rating | `text` + `CHECK`, nullable | 29 fasta värden, se `Rating` |
| systembolaget_product_number | `text`, nullable | |
| systembolaget_description | `text`, nullable | |
| munskankarna_review | `text`, nullable | Egennamn (Munskänkarna) |
| munskankarna_rating | `text` + `CHECK`, nullable | Samma 29 värden som `own_rating` |
| vivino_rating | `numeric(2,1)`, nullable | |
| other_reference | `text`, nullable | |
| location | `text`, nullable | Fritext (Låda 1, Öppen, etc.) |
| search_vector | `tsvector`, triggerunderhållen | Se "Filtrering, sökning och sortering" |
| created_at, updated_at | `timestamptz` | Inte byggda ännu |

Namngivningsprincip: engelska för kolumner/tabeller, men svenska
egennamn som syftar på svenska institutioner behåller sitt svenska namn
(`munskankarna_review`, `systembolaget_*`).

`own_rating`/`munskankarna_rating` är begränsade till exakt de 29
värdena från källfilens `Listor`-flik. `Rating` (`domain/Rating.java`)
har korta konstantnamn (`R16`, `R14_5`) med den fulla svenska etiketten
som ett separat fält; `Rating.fromLabel(text)` normaliserar mellanslag
innan matchning.

Se [ADR 0004](docs/adr/0004-images-in-bytea.md) för varför bilder
lagras i `bytea` och [ADR 0016](docs/adr/0016-quantity-also-mandatory.md)
(som ersätter [ADR 0005](docs/adr/0005-only-name-required.md)) för varför
`name` och `quantity` är de enda obligatoriska fälten.

### Flera användare

Tabell `users` (`id`, `username` unik, `hashed_password`, `created_at`)
- varje `wines`-rad har exakt en ägare (`owner_id`), och en inloggad
användare ser och kan bara ändra sin egen lista. Se
[ADR 0013](docs/adr/0013-multi-user-accounts.md).

## Vinlistan

Startsidan visar en överblick per vin: bild, namn, typ, producent,
land, region, underregion, druvor, årgång, flaskor, eget betyg,
Munskänkarnas betyg och Vivino-betyg. Övriga fält (plats, inköpsdatum,
pris, inköpsanledning, tasting notes, Systembolagets
produktnummer/beskrivning, Munskänkarnas bedömning, annan referens)
visas infällt under en "Detaljer"-sektion på mobil - på desktop visas
alla fält direkt utan infällning.

Layouten växlar mellan en bred fyrkolumnslayout (desktop, >960px) och
en smal kortlayout med infälld Detaljer (mobil, ≤960px) via en CSS
media query, verifierat av `WineListResponsiveIT` (Playwright) - se
[ADR 0002](docs/adr/0002-responsive-list-dual-layout.md).

### Filtrering, sökning och sortering

Verktygsraden ovanför listan har:

- Ett sökfält (fritextsökning över namn, producent, druvor, tasting
  notes, Systembolagets beskrivning och Munskänkarnas bedömning),
  Postgres-driven med böjningsform-medvetenhet - se
  [ADR 0007](docs/adr/0007-fulltext-search-tsvector.md).
- Sorteringskontroller (fält + riktning) för Namn, Producent, Land,
  Årgång, Antal flaskor, Pris, Inköpsdatum, Eget betyg, Munskänkarnas
  betyg och Vivino-betyg. Viner utan värde för det sorterade fältet
  hamnar alltid sist, oavsett riktning.
- En hopfällbar filterpanel med vintyp (fem kryssrutor) och ursprung
  (land→region→underregion, nästlade kryssrutor). Facetter kombineras
  med OCH sinsemellan, ELLER inom en facett. Panelen fälls automatiskt
  ut runt redan valda filter.
- Chips som visar varje aktivt filter-/sökvärde, med en
  borttagningslänk per chip - se
  [ADR 0008](docs/adr/0008-filter-chips-plain-links.md).

Vald sortering/filtrering/sökning hamnar i URL:en
(`?search=...&sort=...&direction=...&wineType=...`) - bokmärkbart och
delbart. Orkestreringen ligger i `WineService.search(SearchCriteria)`,
inte i controllern - se
[ADR 0006](docs/adr/0006-search-orchestration-in-application-layer.md).

## Säkerhet

Hela appen kräver inloggning - formulärbaserad, med session, se
[ADR 0013](docs/adr/0013-multi-user-accounts.md) (ersätter den
ursprungliga HTTP Basic-modellen i
[ADR 0009](docs/adr/0009-whole-app-http-basic-auth.md)). Vem som helst
kan registrera ett eget konto på `/registrera` - varje konto får en
helt privat, egen vinlista, ingen delning och ingen rollindelning
(de tidigare hårdkodade `admin`/`readonly`-kontona och
`WINECELLAR_ADMIN_PASSWORD` är borttagna).

CSRF är påslaget (htmx-formulären skickar token via en
`htmx:configRequest`-lyssnare, se `vinkallare.html`).

Etikettskanningen (se Vinlistan ovan) kräver `WINECELLAR_ANTHROPIC_API_KEY`
- utan den startar appen ändå (tom lokal default), men skanningsanropet
misslyckas. `WINECELLAR_ANTHROPIC_MODEL` är valfri (default
`claude-sonnet-5`).

## Köra lokalt

Kräver en riktig Postgres (se `application.yml`):

```
docker compose up -d
mvn spring-boot:run
```

Öppna http://localhost:8080 - formuläret postar via htmx utan
sidladdning. Databasen är tom från början; lägg till det första vinet
via formuläret.

## Köra tester

```
mvn verify
```

Kör enhetstester/webblagertester (JUnit 5 + AssertJ + MockMvc, via
Surefire) och acceptanstester (Cucumber, via `CucumberIT`, Failsafe).
Persistensscenariot (`vin-persistens.feature`) och
`WineListResponsiveIT` (Playwright) startar egna Postgres-instanser via
Testcontainers - kräver en körande Docker-daemon oavsett om
docker-compose-databasen ovan är igång.

Playwright kräver att webbläsarbinärerna är installerade lokalt (och
som ett steg i CI innan `mvn verify`):

```
mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.classpathScope=test -Dexec.args="install"
```

### Arbetsprocess

1. Gherkin-scenario tillsammans, innan kod skrivs
2. Acceptanstest (Cucumber, `*IT.java`) mot applikationslagret
3. Enhetstest i domänlagret
4. UI-test (`@WebMvcTest` + `MockMvc`) mot stubbat servicelager -
   verifierar faktiskt renderad HTML
5. `WineListResponsiveIT` (Playwright) för CSS-/responsivitetsbeteende
   som `@WebMvcTest`/MockMvc inte kan verifiera

## Import och export av Excel-data

En inloggad användare kan importera/exportera sin egen vinlista som
`.xlsx`, i samma kolumnlayout som den ursprungliga `Vinlista.xlsx` -
en webbfunktion (Fas 2, se
[ADR 0014](docs/adr/0014-web-based-excel-import-export.md)), inte
längre ett fristående kommandoradsverktyg. Den tidigare CLI-modulen
(`tools/import-excel/`, se
[ADR 0010](docs/adr/0010-excel-tool-standalone-module.md), Superseded
av ADR 0014) är borttagen - `WineRowParser`/`WineRowWriter`/
`ImageMatcher` lever kvar som återanvändbar kod i huvudappen
(`infrastructure/excel/`).

Kolumnlayouten (A-U på `Vin`-fliken) är hårdkodad i `WineRowParser`.
Det finns ingen egen bild-kolumn (togs bort i WINE-32 - se
[ADR 0011](docs/adr/0011-excel-image-roundtrip-dual-mechanism.md),
Deprecated) - etikettbilder hanteras uteslutande via
`/export/bilder.zip`/mappuppladdningen, inte via själva kalkylbladet:

| Kolumn | Fält | Kolumn | Fält |
|---|---|---|---|
| A | Vintyp | L | Varför köpt |
| B | Land | M | Tasting notes |
| C | Region | N | Eget betyg |
| D | Underregion | O | Systembolagets prodnummer |
| E | Druvor | P | Systembolagets beskrivning |
| F | Producent | Q | Munskänkarnas bedömning |
| G | Namn | R | Munskänkarnas betyg |
| H | Årgång | S | Vivino |
| I | Inköpsdatum | T | Annan referens |
| J | Pris | U | Plats |
| K | Antal | | |

Namn och antal flaskor är obligatoriska vid import (samma regel som
webb-UI:t, se [ADR 0016](docs/adr/0016-quantity-also-mandatory.md)) - en
rad som saknar något av de två fälten eller på annat sätt inte kan
tolkas hoppas över, utan att stoppa resten av importen.

### Export

- `GET /export/xlsx` - laddar ner den inloggade användarens egna viner
  som en `.xlsx`-fil, sorterade på namn.
- `GET /export/bilder.zip` - laddar ner en zip med etikettbilderna,
  namngivna efter vinets satta fält: `<producent>_<namn>_<årgång>`
  när alla tre finns, `<producent>_<namn>` när årgång saknas,
  `<namn>_<årgång>` när producent saknas, och bara `<namn>` när
  endast namnet är känt. Mellanslag inom producent- och vinnamn
  bevaras; endast separatorn mellan fälten är understreck. Samma
  konvention som importen matchar bilder mot.

Båda länkarna finns direkt i vinlistan. Exporten är byte-exakt -
bilderna som laddas ner är identiska med det som en gång laddades upp.

### Import

`GET /import` visar ett formulär för att ladda upp en `.xlsx`-fil och,
valfritt, en bildmapp (webbläsarens mappväljare). Importen sker i två
steg:

1. **Torrkörning** (`POST /import`) - parsar filen och
   dubblettkontrollerar varje rad mot den inloggade användarens egna
   viner (samma identitet - namn/producent/årgång - som
   dubblettvarningen vid manuellt tillägg av ett enskilt vin), UTAN
   att spara något. Visar en sammanfattning: rader totalt, överhoppade,
   fullständiga/partiella dubbletter, rena nya viner.
2. **Commit** (`POST /import/commit`) - efter att ha valt en
   dubblettstrategi (öka antal / lägg till som nytt / hoppa över -
   separata val för fullständiga och partiella dubbletter) sparas
   raderna faktiskt.

**Bilder i den uppladdade mappen skalas ned och konverteras till JPEG
i webbläsaren innan uppladdning** - annars hade en mapp med många
okomprimerade telefonfoton krockat med uppladdningsgränsen eller
belastat serverminnet. Det gör att en export följt av en re-import
INTE ger tillbaka bit-identiska bilder, bara visuellt likvärdiga - en
medveten avvägning, se
[ADR 0015](docs/adr/0015-bulk-import-images-lossy-jpeg.md).

## Deploy

**Clever Cloud**, GitHub-länkad autodeploy, `clevercloud/maven.json`
pekar ut `spring-boot:run`, PostgreSQL-tillägget länkat till appen.
Kända fällor (HikariCP-poolstorlek, tillägget måste länkas om appen
skapas om) finns dokumenterade i `CLAUDE.md`. Appens URL är medvetet
inte listad här - det här repot är delat.

## Mer information

- `docs/adr/` - arkitektur- och designbeslut med motivering
  (Architecture Decision Records).
- `CLAUDE.md` - detaljerad, kronologisk utvecklingslogg för AI-assisterat
  arbete i repot: kända fällor, testmetodik, och resonemang bakom
  enskilda implementationsval som inte är arkitektoniska nog för en ADR.
