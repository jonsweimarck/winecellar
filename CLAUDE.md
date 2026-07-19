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
  **Status: båda byggda.** Hibernate genererar automatiskt en
  `CHECK`-constraint för `wine_type`/`own_rating`/`munskankarna_rating` från
  `@Enumerated(EnumType.STRING)`, även med `ddl-auto: update` (ingen manuell
  migrering behövdes). `Rating` (`domain/Rating.java`) följer samma mönster
  som `WineType`: korta konstantnamn (`R16`, `R14_5`) som är det Postgres
  faktiskt lagrar/CHECK-constraintar, med den fulla svenska etiketten som
  ett separat `label`-fält - inte tvärtom. `Rating.fraEtikett(text)`
  normaliserar mellanslag innan matchning, eftersom källfilens
  `Listor`-flik har inkonsekvent dubbla mellanslag i några rader (uppenbara
  inmatningsfel, inte meningsfulla skillnader).
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
- **Bilder lagras direkt i `wines`-tabellen** (`image` + `image_mime_type`),
  inte i extern objektlagring (se README för avvägningen).
  **Status: byggt**, del av `vin-formular.html` (inte längre en separat
  `POST /wines/{id}/bild` - se nedan), och verifierat lokalt end-to-end
  (uppladdad och hämtad bild har identiska bytes, `Content-Type` stämmer).
  Viktig detalj som höll på att glömmas vid implementationen:
  `image_mime_type` måste sättas från `MultipartFile.getContentType()` vid
  uppladdning, och samma värde användas som `Content-Type`-header när
  bilden serveras tillbaka - annars visar webbläsaren inte bilden trots
  att bytes finns i databasen. Vinlistan bäddar aldrig in bilddata i
  själva HTML-fragmentet - `<img>` pekar mot `GET /wines/{id}/bild`, så
  listrenderingen förblir lätt även när viner har bilder.
  **`oid`-avvikelsen är fixad (2026-07-17):** `image`-kolumnen var i
  praktiken `oid` (Postgres large object), inte `bytea` - `@Lob private
  byte[]` mappar till `oid` med Hibernates standardinställningar mot
  Postgres, upptäckt via `\d wines` (syntes inte i den ursprungliga
  end-to-end-verifieringen, som bara jämförde bytes via HTTP, inte
  kolumntypen). `WineEntity.image` har bytt från `@Lob` till
  `@JdbcTypeCode(SqlTypes.VARBINARY)`, som ger en riktig `bytea`-kolumn.
  `ddl-auto: update` kan bara lägga till kolumner/tabeller, inte ändra en
  kolumns typ, så en engångsmigrering krävdes för redan existerande data
  - `db/migrations/2026-07-17-image-oid-to-bytea.sql`, se README:s
  "Bilder i bytea, inte objektlagring" för kommandot. Verifierat lokalt:
  en simulerad "gammal" databas (riktig `oid` + `pg_largeobject`-post)
  migrerades korrekt - bytes bevarade, `pg_largeobject` tomt efteråt,
  appen serverar den migrerade bilden och sparar nya bilder som `bytea`.
  **Körd mot produktionsdatabasen (2026-07-17):** `UPDATE 0`/0 rader
  `lo_unlink` - inga bilder fanns ännu i produktion, så det var en ren
  typkonvertering utan data att flytta.
- **`Wine` har 23 fält** (växte från ursprungliga sju via Excel-importen,
  se README:s Datamodell) - en positionell record-konstruktor med den
  längden vore oläsbar och lätt att kasta om av misstag. Använd
  `Wine.builder()...build()` (och `vin.toBuilder()...build()` för
  with-metoder) på alla anropsplatser, inte `new Wine(...)` direkt.
  Motsvarande i `WineEntity`: no-arg-konstruktor + paketprivata settrar
  istället för en lika lång positionell konstruktor - samma resonemang.
  **Status:** alla fält är redigerbara i webb-UI:t via `vin-formular.html`
  - en egen sida, inte ett htmx-fragment i listan, eftersom 23 fält i en
  radform vore ohanterligt. **Samma mall och i praktiken samma sida
  används för både tillägg och redigering** (`GET /wines/nytt` respektive
  `GET /wines/{id}/redigera`), eftersom fälten är identiska - bara
  rubrik/knapptext/formulärets `action` skiljer (avgörs av `vin.id ==
  null` i mallen). Startsidan (`/`) har inget inbäddat formulär längre,
  bara listan och en länk till `/wines/nytt`. `POST /wines` (tillägg) och
  `POST /wines/{id}/redigera` delar en privat
  `tillämpaFormulärfält(...)`-metod i `WineController` istället för att
  duplicera fälttolkningen - skillnaden är bara vilken `Wine.Builder` de
  startar från (`Wine.builder()` tomt vs `befintligt.toBuilder()`).
  Kontrollermetoderna tar emot alla valfria fält som rå `String` och
  tolkar dem själva (blankt fält → `null`) istället för att låta Spring
  binda direkt till `Rating`/`LocalDate`/`BigDecimal` - annars kraschar
  bindningen på en tom sträng från ett oifyllt formulärfält istället för
  att ge `null`. Samma mönster som `VinradParser` använder för
  Excel-celler. Formuläret är `multipart/form-data` och tar emot en
  valfri `MultipartFile bild` i samma anrop - `medBildOmVald(...)` sätter
  bara `image`/`imageMimeType` om en fil faktiskt valdes
  (`!bild.isEmpty()`), annars behåller Builder:n vad den redan hade
  (oförändrad bild vid redigering, ingen bild vid tillägg). De tidigare
  separata `POST /wines/{id}/antal` och `POST /wines/{id}/bild` - och
  motsvarande snabbformulär i `vinkallare.html` - är borttagna; ändra
  antal och ladda upp bild sker numera bara via det gemensamma
  formuläret. `GET /wines/{id}/bild` (visning) finns kvar, den behövs för
  `<img>`-taggarna i både listan och formuläret.
  **Vinlistan visar alla icke-tekniska fält (byggt 2026-07-19, fältfördelningen
  justerad samma dag efter användarfeedback):** översikten i tabell/kort
  visar bild, namn, typ, producent, land, region, underregion, druvor,
  årgång, flaskor, eget betyg, Munskänkarnas betyg och Vivino-betyg -
  geografi- och betygsfälten flyttades hit från detaljvyn eftersom
  användaren vill se dem utan att fälla ut något extra. Resterande fält
  (plats, inköpsdatum, pris, inköpsanledning, tasting notes,
  Systembolagets produktnummer/beskrivning, Munskänkarnas bedömning,
  annan referens - plats flyttades hit från översikten i samma
  ändring) är infällda under en `<details>`-baserad "Detaljer"-sektion
  per rad/kort - `id`, `image`/`image_mime_type` (redan täckta av
  bildminiatyren) och de ännu obyggda `created_at`/`updated_at` är
  medvetet exkluderade helt. Plats visas ovillkorligt i detaljfragmentet
  (obligatoriskt fält, aldrig `null`), till skillnad från de flesta andra
  detaljfälten som bara visas om de faktiskt är satta. Ett delat
  Thymeleaf-fragment
  (`th:fragment="detaljfalt(vin)"` i `vinkallare.html`) återanvänds av
  både tabell- och kortvyn istället för att duplicera fältuppräkningen;
  varje fält visas bara om det är satt (`th:if="${vin.X != null}"`).
  **Fälla:** `th:fragment` döljer inte elementet från normal
  toppnedrendering av sidan - fragmentet ligger som ett syskon till
  `th:fragment="lista"`-diven, utanför alla `th:each`, så utan en extra
  vaktklausul (`th:if="${vin != null}"` på fragmentets rotelement)
  kraschar helsideslaster (`GET /`) med `EL1007E: Property or field
  'region' cannot be found on null`, eftersom `vin` inte är bundet där.
  `th:insert="~{::detaljfalt(${vin})}"` binder parametern korrekt vid
  faktiska anrop, så vaktklausulen slår bara till vid den oavsiktliga
  direktrenderingen.
  **Tabellvyns detaljrad fick egen `<tr>` (fixat 2026-07-19):** den
  ursprungliga varianten la `<details>` i tabellradens sista `<td>`, så
  det uppfällda innehållet klämdes in i den smala kolumnens bredd även
  på en stor skärm - upptäckt av användaren mot den riktiga deployen.
  Fixat genom att låta varje vin rendera **två** `<tr>` (huvudrad +
  `<tr class="detaljrad">` med en enda `<td colspan="14">` som spänner
  hela tabellbredden), grupperade med `<th:block th:each="vin :
  ${viner}">` runt båda raderna - `th:block` renderar ingen egen tagg,
  så resultatet blir en platt sekvens av `<tr>`-element direkt under
  `<tbody>`, vilket är det enda giltiga sättet att upprepa flera
  syskon-rader per Thymeleaf-iteration. `colspan="14"` måste hållas i
  synk med antalet `<th>` i `<thead>` (Bild/Namn/Typ/Producent/Land/
  Region/Underregion/Druvor/Årgång/Flaskor/Eget betyg/Munskänkarnas
  betyg/Vivino-betyg/åtgärdskolumnen) - ändra båda om en kolumn läggs
  till eller tas bort. Detaljernas `<dl>` använder en egen klass
  (`.detaljlista-bred`, `grid-template-columns: repeat(2, auto 1fr)`)
  istället för kortvyns `.vinkort dl`, eftersom den nu har gott om
  bredd att fördela fälten på två kolumner istället för kortvyns en.
  **Kortmallen designad om efter en PNG-mockup (2026-07-19), avgränsad
  till kortvyn - tabellvyn rörs inte.** `.vinkort` gick från ett
  vertikalt stack med `<dl>`-fältetiketter till en flex-rad: en smal
  bildkolumn (`.vinkort-bildyta`, `flex: 0 0 5.5rem`) och en
  textkolumn. De flesta fälten (producent, namn+årgång, ursprung,
  vintyp, druvor) visas som löpande text utan fältetiketter -
  medvetet inkonsekvent med tabellvyns kolumnrubriker, eftersom
  mockupen uttryckligen ville ha den stilen bara på kortet. Betygen
  fick etiketten *ovanför* värdet (`.betyg-label`/`.betyg-varde`,
  båda `display: block`) istället för på samma rad - en första
  version med etikett och värde på samma rad gav radbrytning mitt i
  långa betygstexter (t.ex. `Munskänkarnas betyg`s fulla svenska
  etikett), vilket användaren bad om att få fixat. Antal flaskor
  (inte med i mockupen) löstes efter en avstämning som en badge i
  kortets övre högra hörn (`.flaskor-badge`, `position: absolute;
  top/right: -0.6rem` - flyttades dit från övre vänstra hörnet efter
  en första feedback-runda). "Detaljer" är fortfarande en vanlig
  `<summary>`, men stylad som en understruken länk
  (`.vinkort summary { text-decoration: underline; font-weight:
  normal }`) istället för tabellvyns fetstilta variant, för att matcha
  mockupens länkkänsla - scopead till `.vinkort` så tabellens
  `<summary>` inte påverkas.

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
- **`WINECELLAR_ADMIN_PASSWORD` är satt i Clever Cloud-konsolen och
  verifierad (2026-07-12)**: standardlösenordet `admin`/`admin` ger 401 mot
  produktionsappen, ett riktigt lösenord ger 200. Värt att komma ihåg om
  appen någonsin skapas om i konsolen (ny app = ny uppsättning
  miljövariabler, måste sättas på nytt) - och att Clever Cloud läser
  miljövariabler vid processstart, så en sparad variabel kräver en
  omstart/redeploy av appen för att slå igenom, inte bara att den sparas.

## Excel-import

`tools/import-excel/` är ett **fristående** engångsprogram (Apache POI),
inte en del av den körande applikationen. POI ska inte hamna som
runtime-beroende i den deployade jaren - egen `pom.xml`, **inte** ett
`<module>` av rot-pom.xml (skulle tvinga rotens packaging till "pom" och
göra `clevercloud/maven.json`s `spring-boot:run`-mål meningslöst).

**Status: byggt och verifierat lokalt (2026-07-17).** Beror på
`com.example:winecellar` (rotens artefakt, `mvn install`-ad lokalt) för
att återanvända `Wine`/`WineType`/`Rating` istället för att duplicera
betygslistan. Detta krävde en ändring i **rotens** `pom.xml`:
`spring-boot-maven-plugin` fick `<classifier>exec</classifier>` - utan
den skriver `repackage` (bunden till `package`-fasen, körs alltid före
`install`) över den vanliga jaren med en Boot-fatjar (klasser under
`BOOT-INF/classes/...`), vilket gör den oanvändbar som ett vanligt
Maven-beroende. Klassificeraren påverkar inte `spring-boot:run` (körs mot
`target/classes`, aldrig mot den paketerade jaren) - Clever Cloud-deployen
är opåverkad, verifierat genom en fullständig `mvn verify` efteråt.

Skriver direkt via JDBC mot `wines`-tabellen, inte via `WineService`/HTTP.
Bild-kolumnen (Excels "bild i cell", inbäddad rich data) importeras
medvetet inte - se README:s "Import av befintlig Excel-data" för
kommandon och `VinradParser`/`ImportExcel` för implementationen.

**Körd mot produktionsdatabasen (2026-07-17), 30 viner sparade utan fel.**
Klever Cloud har inget CLI/konsol att köra verktyget *på* - det behövs
inte heller, det körs lokalt och pratar med Postgres-tillägget över
nätverket (nåbart utifrån, samma anslutning som t.ex. DBeaver/psql skulle
använda), pekat mot produktionens `POSTGRESQL_ADDON_*`-uppgifter från
Clever Cloud-konsolen istället för `localhost`. Ingen dedupliceringslogik
i verktyget - kör inte en gång till mot samma databas, det skulle skapa
dubbletter.

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
- **Playwright Javas `Playwright.create()` installerar alla tre
  webbläsarmotorer (Chromium, Firefox, WebKit), inte bara den som faktiskt
  används i testet.** Att bara köra CLI-installationen med `chromium` som
  argument räcker inte - vid nästa `mvn verify` upptäcker drivrutinen att
  Firefox/WebKit saknas och försöker ladda ner dem på nytt, vilket kraschar
  testet om nätverket är begränsat vid det tillfället. Kör installationen
  utan att begränsa till en enskild motor (se README:s "UI-test, utökat med
  Playwright") så slipper man den överraskningen.
- **Clever Cloud injicerar apparens miljövariabler även i byggsteget, inte
  bara vid körning.** `WINECELLAR_ADMIN_PASSWORD` (det riktiga
  produktionslösenordet) fanns alltså tillgängligt när `mvn test` kördes
  under bygget, och `@Value("${winecellar.admin.password}")` plockade upp
  det istället för `application.yml`s lokala default `admin` - varje
  `WebMvcTest` som hårdkodar `httpBasic("admin", "admin")` fick då 401 och
  hela bygget/deployen kraschade (verifierat 2026-07-16, se git-historiken).
  Fixat med `@TestPropertySource(properties =
  "winecellar.admin.password=admin")` på `WineControllerTest` - pinnar
  testlösenordet oavsett vad miljön runt omkring råkar ha satt. Gäller varje
  ny `@WebMvcTest`-klass som autentiserar med hårdkodade testuppgifter.
- **PowerShell trasslar till `-Dexec.args="<flera mellanslagsskilda
  värden>"`** på ett sätt som inte ger ett tydligt citattecken-fel, utan
  ett förvirrande "Plugin ... could not be resolved" från Maven (delar av
  den sönderslagna strängen tolkas som ett plugin-koordinat). Bash hanterar
  samma syntax utan problem - det är PowerShell-specifikt. Lösning: sätt
  flervärdesargument (t.ex. `ImportExcel`s jdbc-url/användare/lösenord) som
  miljövariabler istället och skicka bara ett enda värde (utan mellanslag)
  via `-Dexec.args`, se README:s "Import av befintlig Excel-data".
- **Utan `<meta name="viewport" content="width=device-width,
  initial-scale=1">` triggas aldrig CSS-brytpunkten på riktiga mobila
  webbläsare.** `vinkallare.html` saknade taggen - riktiga telefoner
  renderar då sidan mot en betydligt bredare virtuell yta (~980px, zoomat
  ut) istället för mot den faktiska skärmbredden, så `max-width: 640px`
  aldrig träffade och tabellvyn visades istället för kortvyn (upptäckt av
  användaren på en riktig telefon, inte av `WineListResponsiveIT` - se
  nästa punkt för varför testet missade det). Fixat genom att lägga till
  taggen i `<head>`.
- **Playwrights `setViewportSize(...)` ensamt räcker inte för att fånga
  ovanstående klass av bugg.** Chromium respekterar bara den
  mobilspecifika "ingen viewport-tagg → rendera brett och zooma ut"-
  kvirken när `isMobile(true)` är satt på kontexten - en smal viewport
  utan det flaggan renderar bara bokstavligen smalt, oavsett om HTML:en
  har en viewport-tagg eller ej. `WineListResponsiveIT`s mobilkontext
  hade bara `setViewportSize`, inte `isMobile(true)`, och missade därför
  buggen ovan trots att testet var grönt. Sätt alltid `isMobile(true)`
  (och gärna `setHasTouch(true)`) på mobilkontexter i UI-tester som ska
  spegla en riktig telefon, inte bara en smal skärm.
- **`@Lob private byte[] fält` mappar till Postgres `oid` (large object)
  med Hibernates standardinställningar, inte `bytea`.** Upptäckt via
  `\d wines` - `image`-kolumnen var `oid`, trots att README/CLAUDE.md
  hela tiden sagt `bytea`. Fungerar transparent för upp-/nedladdning via
  JDBC (bytes stämmer), så det syns inte i en end-to-end-verifiering som
  bara testar HTTP-beteendet - bara genom att faktiskt inspektera
  kolumntypen. Risken var föräldralösa poster i `pg_largeobject`
  (Postgres städar inte bort dem automatiskt när raden tas bort eller
  bilden byts ut). Fixat 2026-07-17, se Datamodell-avsnittet ovan. Kom
  ihåg att kontrollera detta explicit (`\d <tabell>`) för framtida
  `@Lob byte[]`-fält, inte bara lita på att applikationsbeteendet ser
  rätt ut.

Se README.md:s "Nästa steg"-sektion - hålls bara på ett ställe.
