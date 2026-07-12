# winecellar

Lärprojekt nummer två i samma serie som `roombooking`: samma process
(Claude Code, Specification by Example, CI/CD), men ett annat fokus. Här är
domänlogiken medvetet enkel (i praktiken CRUD) - det som är den intressanta
utmaningen är istället ett gränssnitt som fungerar lika bra på en
datorskärm som på en mobil.

Ersätter en tidigare Excel-fil (`Vinlista.xlsx`, en rad per vin) med en
webbapp, tillgänglig från nätet, läs- och skrivbar från både dator och
mobil.

## Arkitektur

Samma hexagonala lagerindelning som `roombooking`:

```
domain/          Rena domänobjekt (Wine, WineType), inga ramverksberoenden
application/     Use cases och portar (WineService, WineRepository)
infrastructure/  In-memory-testdubblett + JPA/Postgres-adapter (JpaWineRepository)
web/             Controller + Thymeleaf/htmx
```

Till skillnad från `roombooking` finns här inga affärsregler att tala om -
domänlagret är tunt. Det som gör UI-lagret svårare är istället
responsiviteten, se "UI-test" nedan - men den responsiva table/card-vyn är
ännu inte byggd (se Nästa steg); nuvarande `vinkallare.html` är en enkel,
icke-responsiv tabell. `Rating` (betyg, 29 fasta värden) är en beslutad men
ännu inte byggd domänmodell, se CLAUDE.md.

## Datamodell

Tabell `wines` (engelska namn, plural, genomgående):

| Kolumn | Typ | Kommentar |
|---|---|---|
| id | `bigserial` PK | |
| wine_type | `text` + `CHECK` | Enum i Java: RED, WHITE, ROSE, SPARKLING, FORTIFIED |
| country | `text` | |
| region | `text` | |
| subregion | `text`, nullable | |
| grapes | `text`, nullable | Fritext, ingen normalisering till egen tabell |
| producer | `text` | |
| name | `text` | |
| vintage | `smallint` | |
| image | `bytea`, nullable | Vinetikett, lagras direkt i databasen (se nedan) |
| image_mime_type | `text`, nullable | T.ex. `image/jpeg` - krävs för att kunna servera bilden med rätt `Content-Type` |
| purchase_date | `date` | |
| price | `numeric(10,2)` | |
| quantity | `integer` | Enkel räknare, ändras direkt vid redigering - ingen förbrukningslogg (kan utökas senare om det behövs) |
| purchase_reason | `text`, nullable | |
| tasting_notes | `text`, nullable | |
| own_rating | `text` + `CHECK` | Samma enum som munskankarna_rating |
| systembolaget_product_number | `text`, nullable | Uppdelad från Excelns hopklistrade cell |
| systembolaget_description | `text`, nullable | |
| munskankarna_review | `text`, nullable | Egennamn (Munskänkarna) - medvetet inte översatt |
| munskankarna_rating | `text` + `CHECK` | |
| vivino_rating | `numeric(2,1)`, nullable | |
| other_reference | `text`, nullable | |
| location | `text` | Fritext (Låda 1, Öppen, etc.) - inte enum, växer troligen över tid |
| created_at, updated_at | `timestamptz` | |

**Nuvarande implementationsstatus:** endast `id`, `name`, `wine_type`,
`producer`, `country`, `vintage`, `quantity` och `location` finns i den
körande databasen (`WineEntity`) - de täcker de CRUD-scenarier som är
skrivna hittills. Resten av tabellen ovan är målschemat, inte redan byggt;
kolumner läggs till i takt med att nya Gherkin-scenarier kräver dem (betyg,
bilder, Systembolaget-fält, etc.), inte i en enda stor migrering.

**Namngivningsprincip:** engelska för kolumner/tabeller, men svenska
egennamn som faktiskt syftar på svenska institutioner
(`munskankarna_review`, `systembolaget_*`) behåller sitt svenska namn -
samma princip som att man inte skulle döpa om "IKEA" i en möbelapp.

**Betyg som enum:** `own_rating` och `munskankarna_rating` är begränsade
till exakt de 29 värdena från Excelns `Listor`-flik (t.ex.
`"16 (15 - 17,5 Högklassigt vin)"`), som en Java-enum med en
`CHECK`-constraint som håller databasen i synk. Ingen separat
uppslagstabell - 29 fasta strängar är overengineering att normalisera bort.

**Bilder i `bytea`, inte objektlagring:** medvetet val för en samling i den
här storleksordningen (se diskussion i chatten) - en datakälla, enklare
backup, ingen extra molntjänst. Om samlingen och bildmängden växer kraftigt
är det en isolerad migrering senare (flytta bara bilddatan), inte något vi
bygger beredskap för nu.

## Arbetsprocess

Samma ordning som `roombooking`:

1. Gherkin-scenario tillsammans, innan kod skrivs
2. Acceptanstest (Cucumber, `*IT.java`) mot applikationslagret
3. Enhetstest i domänlagret
4. UI-test (`@WebMvcTest` + `MockMvc`) mot stubbat servicelager - verifierar
   faktiskt renderad HTML

### UI-test, utökat med Playwright

Till skillnad från `roombooking` (där vi medvetet avstod från
Playwright, eftersom htmx-fragmentet var det enda som behövde verifieras)
behövs det här: `@WebMvcTest`/MockMvc kör ingen CSS och kan inte se att
listan faktiskt växlar mellan tabell (desktop) och kort (mobil) vid en viss
brytpunkt. Det är själva poängen med UI:t, så det förtjänar ett eget
testlager:

- **`WineListResponsiveIT`** (Failsafe, `*IT.java`): startar appen
  (`@SpringBootTest(webEnvironment = RANDOM_PORT)`), öppnar sidan med
  Playwright i två viewport-bredder (desktop, mobil) och verifierar vilket
  element (tabell-vy respektive kort-vy) som faktiskt är synligt vid
  respektive bredd.
- Kräver `com.microsoft.playwright:playwright` som testberoende, samt att
  webbläsarbinärerna installeras (`mvn exec:java
  -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"`)
  - görs en gång lokalt och som ett steg i CI innan `mvn verify`.

## Köra lokalt

Produktionskonfigurationen kräver en riktig Postgres (se `application.yml`).
Starta den med docker-compose innan appen startas:

```
docker compose up -d
mvn spring-boot:run
```

Öppna http://localhost:8080 - formuläret postar via htmx utan sidladdning.
Databasen är tom från början; lägg till det första vinet via formuläret på
sidan.

## Köra tester

```
mvn verify
```

Kör både enhetstester/webblagertester (JUnit 5 + AssertJ + MockMvc, via
Surefire) och acceptanstester (Cucumber, via `CucumberIT`, Failsafe).
Persistensscenariot (`vin-persistens.feature`) startar en egen Postgres via
Testcontainers - kräver en körande Docker-daemon, oavsett om
docker-compose-databasen ovan är igång eller inte.

## Import av befintlig Excel-data

`Vinlista.xlsx` importeras **en gång**, inte som en del av den vanliga
CRUD-cykeln. Ett fristående skript (Apache POI), inte en del av den
körande applikationen - POI ska inte vara ett runtime-beroende i den
deployade jaren. Ligger i `tools/import-excel/` som ett eget litet
program, körs manuellt mot databasen en gång och kan sedan tas bort eller
lämnas orörd.

## Deploy

Samma plattform och samma mönster som `roombooking`: **Clever Cloud**,
GitHub-länkad autodeploy, `clevercloud/maven.json` för att peka ut
`spring-boot:run`, PostgreSQL-tillägget länkat till just den här appen.
Samma kända fällor gäller (se `CLAUDE.md`): HikariCPs poolstorlek måste
sänkas, tillägget måste länkas om appen skapas om.

## Nästa steg

- [x] Skriva de första Gherkin-scenarierna tillsammans (lägg till vin, lista
      viner, redigera, ta bort)
- [x] `Wine`-domänobjekt, `WineService`, JPA-adapter (`JpaWineRepository`,
      testad med Testcontainers, se `vin-persistens.feature`)
- [x] Grundläggande webblager (`WineController` + `vinkallare.html`,
      htmx-fragment för lägg till/ändra antal/ta bort, `@WebMvcTest`)
- [ ] Responsiv table/card-mall + `WineListResponsiveIT` - nuvarande
      `vinkallare.html` är en enkel, icke-responsiv tabell
- [ ] Bilduppladdning och -visning (`bytea` + `image_mime_type`)
- [ ] Excel-importskript (`tools/import-excel/`)
- [ ] Autentisering - inte beslutat än. Personlig app för en användare;
      avgör om det behövs alls innan något byggs
- [ ] Deploy till Clever Cloud
