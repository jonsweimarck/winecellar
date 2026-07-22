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
infrastructure/  In-memory-testdubblett + JPA/Postgres-adapter (JpaWineRepository)
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

## Datamodell

Tabell `wines`:

| Kolumn | Typ | Kommentar |
|---|---|---|
| id | `bigserial` PK | |
| wine_type | `text` + `CHECK`, nullable | Enum: RED, WHITE, ROSE, SPARKLING, FORTIFIED |
| country | `text`, nullable | |
| region | `text`, nullable | |
| subregion | `text`, nullable | |
| grapes | `text`, nullable | Fritext |
| producer | `text`, nullable | |
| name | `text`, **NOT NULL** | Enda obligatoriska fältet |
| vintage | `smallint`, nullable | `Integer` i Java |
| image | `bytea`, nullable | Vinetikett |
| image_mime_type | `text`, nullable | T.ex. `image/jpeg` |
| purchase_date | `date`, nullable | |
| price | `numeric(10,2)`, nullable | |
| quantity | `integer`, nullable | `Integer` i Java |
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
| search_vector | `tsvector`, genererad | Se "Filtrering, sökning och sortering" |
| created_at, updated_at | `timestamptz` | Inte byggda ännu |

Namngivningsprincip: engelska för kolumner/tabeller, men svenska
egennamn som syftar på svenska institutioner behåller sitt svenska namn
(`munskankarna_review`, `systembolaget_*`).

`own_rating`/`munskankarna_rating` är begränsade till exakt de 29
värdena från källfilens `Listor`-flik. `Rating` (`domain/Rating.java`)
har korta konstantnamn (`R16`, `R14_5`) med den fulla svenska etiketten
som ett separat fält; `Rating.fraEtikett(text)` normaliserar mellanslag
innan matchning.

Se [ADR 0004](docs/adr/0004-images-in-bytea.md) för varför bilder
lagras i `bytea` och [ADR 0005](docs/adr/0005-only-name-required.md)
för varför bara `name` är obligatoriskt.

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
(`?sok=...&sortera=...&riktning=...&wineType=...`) - bokmärkbart och
delbart. Orkestreringen ligger i `WineService.sök(Sökkriterier)`, inte
i controllern - se
[ADR 0006](docs/adr/0006-search-orchestration-in-application-layer.md).

## Säkerhet

Hela appen kräver HTTP Basic-inloggning - se
[ADR 0009](docs/adr/0009-whole-app-http-basic-auth.md). Två konton:

- `admin` - full åtkomst, lösenord via `WINECELLAR_ADMIN_PASSWORD`
  (miljövariabel i produktion, `admin` som lokal default).
- `readonly`/`readonly` - får se listan och bilder, nekas allt annat.

CSRF är avstängt globalt (htmx-formulären skickar ingen CSRF-token,
autentiseringen är stateless Basic-auth per anrop).

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

`tools/import-excel/` är en fristående Maven-modul (egen `pom.xml`,
inte en del av den deployade appen) - se
[ADR 0010](docs/adr/0010-excel-tool-standalone-module.md). Bygg roten
lokalt först:

```powershell
cd C:\projects\winecellar
mvn install -DskipTests
```

Kolumnlayouten (A-V på `Vin`-fliken) är hårdkodad i `VinradParser`:

| Kolumn | Fält | Kolumn | Fält |
|---|---|---|---|
| A | Vintyp | L | Antal |
| B | Land | M | Varför köpt |
| C | Region | N | Tasting notes |
| D | Underregion | O | Eget betyg |
| E | Druvor | P | Systembolagets prodnummer |
| F | Producent | Q | Systembolagets beskrivning |
| G | Namn | R | Munskänkarnas bedömning |
| H | Årgång | S | Munskänkarnas betyg |
| I | Bild (läses ej vid import) | T | Vivino |
| J | Inköpsdatum | U | Annan referens |
| K | Pris | V | Plats |

Bara namnet är obligatoriskt vid import (samma regel som webb-UI:t, se
[ADR 0005](docs/adr/0005-only-name-required.md)) - en rad utan namn
hoppas över med en utskriven varning.

### Import

**I PowerShell** - sätt anslutningen som miljövariabler och skicka bara
filsökvägen som argument (annars trasslar PowerShells
citattecken-hantering med `-Dexec.args`):

```powershell
$env:POSTGRESQL_ADDON_HOST = "<host>"
$env:POSTGRESQL_ADDON_PORT = "<port>"
$env:POSTGRESQL_ADDON_DB = "<databasnamn>"
$env:POSTGRESQL_ADDON_USER = "<användare>"
$env:POSTGRESQL_ADDON_PASSWORD = "<lösenord>"
$env:WINECELLAR_LOCAL_IMAGE_FOLDER = "C:\Users\jonsw\Documents\Vin\Etiketter"  # valfri, se nedan

cd tools\import-excel
mvn exec:java "-Dexec.args=C:\Users\jonsw\Documents\Vin\Vinlista.xlsx"
```

**I Bash** går flervärdesargumentet att skicka direkt:

```bash
cd tools/import-excel
mvn exec:java -Dexec.args="<sökväg-till-Vinlista.xlsx> <jdbc-url> <användare> <lösenord>"
```

Utan `jdbc-url`/`användare`/`lösenord` som argument används
`POSTGRESQL_ADDON_*`-miljövariablerna, annars
`localhost`/`winecellar`/`winecellar` (docker-compose-databasen).
Verktyget har ingen dedupliceringslogik - kör inte importen två gånger
mot samma databas.

`WINECELLAR_LOCAL_IMAGE_FOLDER` (valfri) pekar ut en mapp med bildfiler
döpta exakt som respektive vins namn (t.ex. `Barolo.jpg`) -
`jpg`/`jpeg`/`png`/`gif`/`webp` känns igen. Matchning är exakt
(filnamnsstam mot `name`, ingen normalisering). Två tvetydighetsfall
hoppas över med en utskriven varning: flera bildfiler med samma stam
men olika ändelse, och flera viner med exakt samma namn (samma bildfil
kopplas då till alla).

### Export

`ExportExcel` skriver `wines`-tabellen till en `.xlsx`-fil i samma
kolumnlayout som importen förväntar sig - se
[ADR 0011](docs/adr/0011-excel-image-roundtrip-dual-mechanism.md) för
hur bilder hanteras. Kräver ett explicit `-Dexec.mainClass`-argument
(annars körs `ImportExcel` som standard):

```powershell
cd C:\projects\winecellar\tools\import-excel

$env:POSTGRESQL_ADDON_HOST = "<host>"
$env:POSTGRESQL_ADDON_PORT = "<port>"
$env:POSTGRESQL_ADDON_DB = "<databasnamn>"
$env:POSTGRESQL_ADDON_USER = "<användare>"
$env:POSTGRESQL_ADDON_PASSWORD = "<lösenord>"
$env:WINECELLAR_LOCAL_IMAGE_FOLDER = "C:\Users\jonsw\Documents\Vin\Etiketter"  # valfri, se nedan

mvn exec:java "-Dexec.mainClass=com.example.winecellar.importexcel.ExportExcel" "-Dexec.args=C:\Users\jonsw\Documents\Vin\Vinlista-export.xlsx"
```

Utan `POSTGRESQL_ADDON_*`-miljövariabler används
`localhost`/`winecellar`/`winecellar`. Bilder skrivs till
`WINECELLAR_LOCAL_IMAGE_FOLDER` om variabeln är satt (mappen skapas
automatiskt om den inte finns) - **samma mapp som import använder**,
och det är den som gör en efterföljande återimport bildmedveten. Bilder
bäddas dessutom alltid in direkt i xlsx-filens "Bild"-kolumn där
formatet stöds (JPEG/PNG/GIF, inte WEBP) - bara en visuell bekvämlighet
för att bläddra i Excel, läses inte tillbaka vid import.

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
