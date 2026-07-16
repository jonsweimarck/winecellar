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
domain/          Rena domänobjekt (Wine, WineType, Rating), inga ramverksberoenden
application/     Use cases och portar (WineService, WineRepository)
infrastructure/  In-memory-testdubblett + JPA/Postgres-adapter (JpaWineRepository)
web/             Controller + Thymeleaf/htmx
```

Till skillnad från `roombooking` finns här inga affärsregler att tala om -
domänlagret är tunt. Det som gör UI-lagret svårare är istället
responsiviteten, se "UI-test" nedan - `vinkallare.html` renderar både en
tabellvy och en kortvy i samma HTML-fragment och växlar mellan dem med en
CSS media query vid 640px, verifierat av `WineListResponsiveIT`.

`Wine` har vuxit till 23 fält i takt med att Excel-importen (se nedan)
krävde dem - för många för en läsbar positionell record-konstruktor, så
`Wine.builder()...build()` används på alla anropsplatser istället för
`new Wine(...)`. De flesta av de nyare fälten (betyg, tasting notes,
Systembolaget-info m.m.) sätts bara av importskriptet - webb-UI:t
redigerar fortfarande bara de ursprungliga sju fälten plus bild.

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

**Nuvarande implementationsstatus:** alla kolumner ovan finns i den körande
databasen (`WineEntity`) **utom** `created_at`/`updated_at` - de är inte
byggda, ingen skriven Gherkin-scenario har krävt dem än. Resten kom i
omgångar (CRUD-fälten, sedan bild, sedan resten via Excel-importen), inte
i en enda stor migrering - se `tools/import-excel/`.

**Namngivningsprincip:** engelska för kolumner/tabeller, men svenska
egennamn som faktiskt syftar på svenska institutioner
(`munskankarna_review`, `systembolaget_*`) behåller sitt svenska namn -
samma princip som att man inte skulle döpa om "IKEA" i en möbelapp.

**Betyg som enum (byggt):** `own_rating` och `munskankarna_rating` är
begränsade till exakt de 29 värdena från Excelns `Listor`-flik. `Rating`
(`domain/Rating.java`) har korta konstantnamn (`R16`, `R14_5` osv. - samma
mönster som `WineType`s `RED`/`WHITE`) med den fullständiga svenska
etiketten (t.ex. `"16 (15 - 17,5 Högklassigt vin)"`) som ett fält;
`Rating.fraEtikett(text)` normaliserar bort inkonsekvent mellanslag i
källfilen (några av "Enkel vin"-raderna har dubbla mellanslag) innan den
matchar. `@Enumerated(EnumType.STRING)` gör att Hibernate genererar
`CHECK`-constrainten automatiskt, precis som för `WineType`. Ingen separat
uppslagstabell - 29 fasta strängar är overengineering att normalisera bort.

**Bilder i `bytea`, inte objektlagring:** medvetet val för en samling i den
här storleksordningen (se diskussion i chatten) - en datakälla, enklare
backup, ingen extra molntjänst. Om samlingen och bildmängden växer kraftigt
är det en isolerad migrering senare (flytta bara bilddatan), inte något vi
bygger beredskap för nu.

**Uppladdning och visning (byggt):** `POST /wines/{id}/bild` tar emot en
`multipart/form-data`-uppladdning (fältnamn `bild`), sätter `image` och
`image_mime_type` från `MultipartFile` och sparar via `WineService.save`.
`GET /wines/{id}/bild` serverar bytes tillbaka med `Content-Type` satt från
`image_mime_type` (404 om vinet saknar bild). `vinkallare.html` visar en
`<img>`-tagg mot den GET-routen när `vin.harBild()` är sant, annars en
textplatshållare - bilddatan skickas alltså aldrig inbäddad i själva
listfragmentet, bara via webbläsarens egna bildförfrågningar.
`spring.servlet.multipart.max-file-size`/`max-request-size` är satta till
5 MB i `application.yml` som en enkel gräns mot orimligt stora
uppladdningar.

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
brytpunkt. Det är själva poängen med UI:t, så det har ett eget testlager:

- **`WineListResponsiveIT`** (Failsafe, `*IT.java`): startar appen
  (`@SpringBootTest(webEnvironment = RANDOM_PORT)`), öppnar sidan med
  Playwright i två viewport-bredder (1280×800 för desktop, 375×667 för
  mobil) och verifierar vilket element (`#vinlista-tabell` respektive
  `#vinlista-kort`) som faktiskt är synligt vid respektive bredd. Egen
  Testcontainers-Postgres, oberoende av Cucumber-suitens.
- Kräver `com.microsoft.playwright:playwright` som testberoende, samt att
  webbläsarbinärerna installeras en gång lokalt (och som ett steg i CI
  innan `mvn verify`):
  ```
  mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.classpathScope=test -Dexec.args="install"
  ```

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
CRUD-cykeln. `tools/import-excel/` är en helt fristående Maven-modul
(egen `pom.xml`, inte ett `<module>` av rot-pom.xml) - POI och en
JDBC-drivrutin är beroenden av *den*, inte av den deployade appen.

Den beror på huvudprojektets egna `com.example:winecellar`-artefakt för
att återanvända `Wine`/`WineType`/`Rating` (rena domänobjekt) istället för
att duplicera betygslistan och mappningslogiken. Roten måste därför vara
`mvn install`-ad lokalt innan importmodulen byggs - se
`spring-boot-maven-plugin`s `<classifier>exec</classifier>`-konfiguration
i rot-`pom.xml`: utan den skriver `repackage` över den vanliga jaren med
en Boot-fatjar som inte går att bero på som vanligt bibliotek.

Kör en gång, manuellt, mot en riktig databas:

```
mvn install -DskipTests                      # från repo-roten, en gång
cd tools/import-excel
mvn exec:java -Dexec.args="<sökväg-till-Vinlista.xlsx> [jdbc-url] [användare] [lösenord]"
```

Utan `jdbc-url`/`användare`/`lösenord` används `POSTGRESQL_ADDON_*`-
miljövariablerna (samma konvention som `application.yml`), annars
`localhost`/`winecellar`/`winecellar` (docker-compose-databasen).

Kolumnlayouten (A-U på `Vin`-fliken) är hårdkodad i `VinradParser` - se
README:s Datamodell-avsnitt för vilket fält varje kolumn motsvarar.
Rader som saknar vintyp, land, producent eller namn hoppas över med en
utskriven varning (ofullständiga utkastrader förekommer i källfilen).
Etikett-kolumnen (`Bild`) importeras **inte** - Excels "bild i cell" är
inbäddad rich data, inte ett vanligt cellvärde, och att extrahera den
robust är inte värt det för ett engångsskript. Ladda upp etiketterna
manuellt via webb-UI:t (`POST /wines/{id}/bild`) efteråt istället.

Verifierat lokalt (2026-07-17) mot en tom docker-compose-databas: 28 av
30 rader importerade (2 ofullständiga utkastrader korrekt överhoppade),
alla fält - inklusive betyg, Systembolagets hopklistrade cell och
prisceller med extra anteckningstext - stämde vid stickprov mot källfilen,
och appen renderade listan felfritt efteråt. Inte körd mot
produktionsdatabasen - det är ett medvetet separat, manuellt steg.

## Deploy

Samma plattform och samma mönster som `roombooking`: **Clever Cloud**,
GitHub-länkad autodeploy, `clevercloud/maven.json` för att peka ut
`spring-boot:run`, PostgreSQL-tillägget länkat till just den här appen.
Samma kända fällor gäller (se `CLAUDE.md`): HikariCPs poolstorlek måste
sänkas, tillägget måste länkas om appen skapas om.

**Deployen är verifierad fungerande (2026-07-12):** riktig Postgres,
GitHub-länkad autodeploy, `spring-boot:run` via `clevercloud/maven.json`,
HTTP Basic-autentisering med ett riktigt lösenord satt via
`WINECELLAR_ADMIN_PASSWORD` i Clever Cloud-konsolen (verifierat att
standardlösenordet `admin`/`admin` **inte** längre fungerar, se
CLAUDE.md:s "Säkerhet"). Appens URL är medvetet inte listad här - det här
repot är delat.

## Nästa steg

- [x] Skriva de första Gherkin-scenarierna tillsammans (lägg till vin, lista
      viner, redigera, ta bort)
- [x] `Wine`-domänobjekt, `WineService`, JPA-adapter (`JpaWineRepository`,
      testad med Testcontainers, se `vin-persistens.feature`)
- [x] Grundläggande webblager (`WineController` + `vinkallare.html`,
      htmx-fragment för lägg till/ändra antal/ta bort, `@WebMvcTest`)
- [x] Responsiv table/card-mall + `WineListResponsiveIT` - `vinkallare.html`
      växlar mellan tabellvy och kortvy vid 640px, verifierat med Playwright
      i två viewport-bredder
- [x] Bilduppladdning och -visning (`bytea` + `image_mime_type`) -
      `POST`/`GET /wines/{id}/bild`, se Datamodell ovan
- [x] Excel-importskript (`tools/import-excel/`) - fristående Maven-modul,
      `Wine` utökad till 23 fält (`Rating`-enum m.m.) för att rymma hela
      Vinlista.xlsx, verifierat lokalt - se "Import av befintlig Excel-data"
- [x] Autentisering (se CLAUDE.md:s "Säkerhet") - HTTP Basic på hela appen,
      inte bara en admin-del, eftersom det inte finns någon publik läsvy
      här och appen redan var nåbar från nätet
- [x] Deploy till Clever Cloud (se "Deploy" ovan) - appen GitHub-länkad,
      verifierad fungerande mot en riktig Postgres
