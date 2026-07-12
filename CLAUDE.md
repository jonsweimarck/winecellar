# Kontext för Claude Code

Andra projektet i samma lärserie som `roombooking`. Se @README.md för
arkitektur, datamodell och arbetsprocess. Många konventioner är rakt
återanvända från `roombooking` - de är inte upprepade i detalj här, bara
flaggade som gällande.

## Återanvänt från roombooking, gäller även här

- Hexagonal lagerindelning (`domain/`, `application/`, `infrastructure/`,
  `web/`), `record`/`sealed interface` framför ceremoni.
- Gherkin-scenario tillsammans med utvecklaren innan kod skrivs.
- Surefire/Failsafe-uppdelningen: `*Test.java` (enhetstest, Surefire) vs
  `*IT.java` (acceptans-/UI-test, Failsafe, kräver `mvn verify`).
- `@WebMvcTest` + `@MockBean` för webblagret, verifierar faktiskt renderad
  HTML - inte bara `Model`-attribut.
- Clever Cloud-deployen, inklusive samma gotchas (HikariCP-poolstorlek,
  PostgreSQL-tillägget måste länkas till den specifika appen,
  `clevercloud/maven.json` med `spring-boot:run`).

## Vad som är nytt/annorlunda här - läs detta innan du antar att roombooking-mönstret gäller rakt av

- **Domänlagret är tunt.** Det finns i praktiken inga affärsregler att
  skydda - det här är CRUD. Bygg inte in skyddsmekanismer eller
  abstraktioner som roombooking hade av domänskäl (t.ex. `Clock`-injicering
  för tidsberoende regler) - de har ingen motsvarighet här.
- **UI-komplexiteten ligger i responsiviteten, inte i logiken.** Se
  README:s "UI-test, utökat med Playwright" - `WineListResponsiveIT` är ett
  nytt testlager som roombooking inte hade, eftersom roombooking aldrig
  behövde verifiera CSS-beteende (bara ett htmx-fragments innehåll).

## Namngivning

- Tabell- och kolumnnamn på engelska, plural för tabellnamn (`wines`).
- **Undantag:** svenska egennamn som syftar på svenska institutioner
  behåller sitt svenska namn rakt av - `munskankarna_review`,
  `munskankarna_rating`, `systembolaget_product_number`,
  `systembolaget_description`. Översätt inte dessa till påhittade engelska
  motsvarigheter ("association_review" etc.) - det är fel typ av
  konsekvens; ett egennamn ska vara igenkännbart, inte översatt.

## Domänmodell - viktiga vägval

- **`WineType`** (Java-enum: RED, WHITE, ROSE, SPARKLING, FORTIFIED) och
  **betyg** (`own_rating`/`munskankarna_rating`, en enum med exakt de 29
  värdena från källfilens `Listor`-flik, t.ex.
  `"16 (15 - 17,5 Högklassigt vin)"`) är medvetet begränsade till fasta,
  slutna mängder - både som Java-enum och som `CHECK`-constraint i
  databasen. Lägg inte till en separat uppslagstabell för dessa - 29 fasta
  strängar är overengineering att normalisera bort.
  **Status:** `WineType` är byggt - Hibernate genererar automatiskt en
  `CHECK`-constraint för `wine_type` från `@Enumerated(EnumType.STRING)`,
  även med `ddl-auto: update` (ingen manuell migrering behövdes). Betyg
  (`own_rating`/`munskankarna_rating`) är fortfarande bara ett beslut, inte
  byggt - ingen skriven Gherkin-scenario har krävt det än.
- **`WineService` har en enda `save`-metod, inte separata `addWine`/
  `updateWine`.** Domänlagret är tunt (se ovan) - det finns ingen skillnad
  i validering eller sidoeffekter mellan att skapa och uppdatera ett vin,
  så två identiskt implementerade metoder vore bara två namn på samma sak.
  Om det uppstår en verklig skillnad senare (t.ex. att tillägg ska vägra
  dubbletter) är det dags att spjälka upp dem igen - inte innan.
- **`location`** (var flaskan förvaras) är **inte** en enum, till skillnad
  från ovanstående - det är fritext eftersom lådor/förvaringsplatser
  förväntas läggas till över tid.
- **`quantity`** är en enkel räknare som ändras direkt vid redigering.
  Medvetet inget förbrukningslogg (datum när en flaska dracks) - om det
  blir aktuellt senare är det en ny, separat tabell (`wine_consumptions`
  eller liknande), inte en ombyggnad av `wines`.
- **Bilder lagras som `bytea` + `image_mime_type` direkt i `wines`-tabellen**,
  inte i extern objektlagring (se README för avvägningen). Viktig detalj
  att inte glömma vid implementation: `image_mime_type` måste sättas från
  `MultipartFile.getContentType()` vid uppladdning, och samma värde
  användas som `Content-Type`-header när bilden serveras tillbaka -
  annars visar webbläsaren inte bilden trots att bytes finns i databasen.

## Säkerhet

- **Hela appen kräver HTTP Basic-inloggning** via `SecurityConfig`
  (`.anyRequest().authenticated()`) - till skillnad från roombooking (som
  bara skyddade `/admin/**`) finns här inget legitimt anonymt
  användningsfall. Appen har ingen separat publik läsvy, så varje route
  låter en besökare ändra vinsamlingen - och appen var redan nåbar från
  nätet innan detta beslut togs. Ett enda konto, lösenord från
  `winecellar.admin.password`/miljövariabeln `WINECELLAR_ADMIN_PASSWORD`
  (default `admin` bara lokalt).
- CSRF är avstängt globalt, av samma skäl som roombooking: htmx-formulären
  skickar ingen CSRF-token, och autentiseringen är stateless Basic-auth per
  anrop - inte en inloggad session som CSRF-skyddet är till för.
- **Kom ihåg att sätta `WINECELLAR_ADMIN_PASSWORD` i Clever Cloud-konsolen**
  efter en deploy av detta - annars skyddas produktionsappen bara av
  standardlösenordet `admin`/`admin`.

## Excel-import

`tools/import-excel/` är ett **fristående** engångsprogram (Apache POI),
inte en del av den körande applikationen. POI ska inte hamna som
runtime-beroende i den deployade jaren - håll det i en egen katalog/modul
som inte packas med i `spring-boot:run`.

## Kända fällor att vara uppmärksam på (ärvda från roombooking, kan återkomma)

- **Gherkin på svenska kräver `# language: sv`** som absolut första rad i
  varje `.feature`-fil.
- `junit-platform-suite-engine` måste vara ett explicit beroende, inte bara
  `junit-platform-suite`.
- **Mockito + nya JDK-versioner**: lås `mockito.version` och
  `net.bytebuddy:byte-buddy(-agent)` om `@MockBean` börjar ge "Byte Buddy
  could not instrument all classes" lokalt.
- **`cucumber-spring` kräver exakt en `@CucumberContextConfiguration`-klass
  så fort den finns på classpath** - annars kraschar hela Cucumber-suiten,
  inte bara de scenarier som faktiskt behöver Spring. roombookings historik
  (`git log`) visar att beroendet lades till *samtidigt* som
  `JpaBookingRepository`, inte innan. Håll samma ordning här: lägg inte till
  `cucumber-spring`/Testcontainers-Postgres förrän ett persistensscenario
  faktiskt skrivs, annars tvingas rena CRUD-scenarier boota en full
  Spring-kontext (och kräva en databas) helt i onödan.
- **`@WebMvcTest`-slice-tester ser inte `SecurityConfig` automatiskt.**
  Utan `@Import(SecurityConfig.class)` slår Spring Boots egen
  standardsäkerhet in istället - den kräver autentisering på *allt* bakom
  ett slumpat genererat lösenord - och redan gröna kontrollertester börjar
  plötsligt få 401. Se `WineControllerTest` för mönstret; gäller varje ny
  `@WebMvcTest`-klass.

## Nästa steg

Se README.md:s "Nästa steg"-sektion - hålls bara på ett ställe.
