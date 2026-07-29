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
  `docs/adr/0002-responsive-list-dual-layout.md` - `WineListResponsiveIT`
  är ett nytt testlager som roombooking inte hade, eftersom roombooking
  aldrig behövde verifiera CSS-beteende (bara ett htmx-fragments
  innehåll).

## Namngivning

- Tabell- och kolumnnamn på engelska, plural för tabellnamn (`wines`).
- **Undantag:** svenska egennamn som syftar på svenska institutioner
  behåller sitt svenska namn rakt av - `munskankarna_review`,
  `munskankarna_rating`, `systembolaget_product_number`,
  `systembolaget_description`. Översätt inte dessa till påhittade engelska
  motsvarigheter ("association_review" etc.) - det är fel typ av
  konsekvens; ett egennamn ska vara igenkännbart, inte översatt.
- **Språket i koden är engelska, rakt igenom - inte bara i domänlagret
  (byggt 2026-07-23, WINE-4).** Applikationslagret hade drivit iväg mot
  svenska klass-/metod-/fältnamn (`Sökkriterier`, `Sorteringsfält`,
  `SorteringsRiktning`, `HärkomstNod`, `WineService.sök(...)`,
  `WineController.vinkällare(...)` m.fl.), medan domänlagret redan var
  konsekvent engelskt - WINE-4 städade bort den snedheten. Regeln
  framåt, för all ny kod i `domain/`, `application/`, `infrastructure/`,
  `web/` och testkoden (klassnamn, metodnamn, fält-/variabelnamn,
  enum-konstanter, Thymeleafs modellattributnycklar och URL-
  frågeparametrar som exponerar dem, t.ex. `sok→search`,
  `sortera→sort`, `riktning→direction`, `viner→wines`): engelska.
  **Tre undantag, oförändrade av WINE-4:**
  1. Kodkommentarer och Javadoc förblir på svenska (som hela den här
     filen visar).
  2. Cucumber-stegdefinitionernas `@Given`/`@När`/`@Så`/`@Och`-uttryck
     (den Gherkin-matchande textsträngen) och samtliga `.feature`-filer
     förblir på svenska - de är den del av testsviten som avsiktligt
     ska vara läsbar för icke-utvecklare. De underliggande Java-
     metodnamnen för själva stegdefinitionerna lämnades också
     oförändrade (t.ex. `attKällarenInnehållerFöljandeViner`) eftersom
     de är namngivna direkt efter Gherkin-frasen de matchar - men lokala
     variabler/fält/privata hjälpmetoder i samma stegklasser (t.ex.
     `SearchAndFilterSteps`, tidigare `SökOchFilterSteps`) är omdöpta
     till engelska, liksom själva klassnamnen.
  3. Egennamn som redan var etablerade undantag (Systembolaget,
     Munskänkarna, se ovan) förblir oöversatta.
  CSS-klassnamn, HTML-`id`/`class`-attribut och den svenska UI-texten
  användaren faktiskt ser (etiketter, knapptexter, `<title>` osv.)
  rördes INTE av WINE-4 - det är produktens språk mot en svensk
  användare, inte kodens ubiquitous language, och principerna gäller
  olika saker. **Historiska CLAUDE.md-poster nedan som nämner de gamla
  svenska namnen (`Sökkriterier`, `.harBild()`, `VinradParser` osv.) är
  medvetet INTE uppdaterade** - den här filen är en kronologisk logg
  (se README:s "Mer information"), och poster ska spegla vad som var
  sant/korrekt när de skrevs, inte skrivas om i efterhand. Lita på
  `git log`/den faktiska koden för nuvarande namn, inte på äldre
  loggposter.

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
- **Bara `name` är obligatoriskt (byggt 2026-07-22, på användarens
  begäran - "årgång" var tvingande, irriterande för att snabbt kunna
  logga ett vin och fylla i resten senare).** `Wine.vintage`/
  `Wine.quantity` gick från primitiv `int` till `Integer` - motiverat
  rakt av (en primitiv kan inte representera "inget värde ännu", till
  skillnad från de redan nullable String-fälten). `WineController.
  tillämpaFormulärfält` tar nu emot ALLA fält (inklusive `wineType`/
  `producer`/`country`/`location`, som tidigare var direkt bundna,
  icke-nullable `@RequestParam`) som rå `String` och tolkar dem själv
  (`tolkaHeltal`, `tolkaVinTyp`) - samma mönster som redan fanns för
  betyg/datum/decimaltal, nu utökat till alla fält.
  **Följdeffekter som krävde separata fixar, inte bara typändringen:**
  - `schema.sql` fick två `ALTER TABLE ... DROP NOT NULL`-satser
    (vintage, quantity) - Hibernate satte NOT NULL automatiskt när
    fälten var primitiver, och `ddl-auto: update` lättar aldrig på en
    befintlig begränsning bara för att Java-typen ändras.
  - `WineService.härkomstträd()` måste hoppa över viner med `null`
    land explicit (`if (vin.country() == null) continue;`) - `TreeMap`
    kastar på en `null`-nyckel, och ett vin utan land kan ändå inte
    placeras i något land-/regiongren i filterträdet.
  - `vinkallare.html` fick `th:if`-vakter på flera ställen som tidigare
    antog fältet alltid var satt (vintyp-`th:switch` hade kraschat rakt
    av på `null`, flaskbadge, producent-rad, land-span, årgångs-span).
    **Testfälla:** `.vk-plats` (de breda kortens Plats-fält) visade en
    tom etikett utan vakt - missades av den första manuella
    verifieringsrundan (som bara kollade att sidan inte kraschade) och
    upptäcktes först via en Playwright-skärmdump som visade en synlig
    men tom "Plats"-rad. Lärdom: att en sida inte kraschar bevisar inte
    att alla fält renderas snyggt - en skärmdump av det FAKTISKA
    minimala fallet (inte bara ett test som kollar HTTP 200) behövs för
    den sortens bugg.
  - `WineControllerTest` fick en ny testfälla att undvika: ett minimalt
    testvin (`Wine.builder().name(...).build()`, utan `.id(...)`)
    kraschade rendering med `EL1007E: Property or field 'value' cannot
    be found on null` på `${vin.id.value}` - ett riktigt sparat vin har
    alltid ett id, så testvin utan `.id(new WineId(1L))` är orealistiskt
    och inte samma sak som att testa "fält som saknas".
- **Mobil saknade horisontell padding helt (fixat 2026-07-22, upptäckt
  av användaren mot produktionen) - `body` ligger an mot skärmkanterna
  utan den.** `body`s enda layoutregel var `max-width: 70rem; margin:
  3rem auto;` - `margin: ... auto` centrerar bara när `body` är SMALARE
  än sin förälder, vilket aldrig händer på mobil (`body` fyller alltid
  hela viewporten där). Upptäcktes inte i den breda vyn eftersom
  `.vinkort-bred` aldrig visas under 960px, och kortvyns egna kort redan
  hade `border`/`padding` som gav dem ett visst visuellt "andrum" även
  utan yttre marginal - men verktygsraden/filterpanelens fullbredds
  `<input>`/`<select>`-fält och flaskbadgens `position: absolute; right:
  -0.6rem` (som faktiskt klipptes bort utanför viewporten) gjorde
  problemet tydligt. Fixat med `body { padding: 0 1rem; }`, men
  **scopeat till `@media (max-width: 960px)`, inte satt globalt** - en
  global padding hade krympt innehållsytan innanför den breda vynens
  noggrant avstämda `max-width: 70rem` och riskerat att de fasta
  18rem-betygskolumnerna inte längre får plats (se "Tabellvyns
  designomgång" i README). Verifierat med Playwright-skärmdumpar i båda
  breddlägena: mobil får ett litet, jämnt andrum utan att desktop-vyn
  påverkas alls.
- **Filtrering/sökning/sortering orkestreras i `WineService`, inte i
  `WineController`** (byggt 2026-07-21, sortering först - se README:s
  "Filtrering, sökning och sortering" för ordningen på de tre
  deltillägget). Beslutet togs medvetet efter en explicit avvägning:
  Gherkin-/Cucumber-scenarierna testar redan mot applikationslagret, inte
  mot HTTP (se `CucumberSpringConfiguration`s kommentar och README:s
  Arbetsprocess) - hade orkestreringen legat i controllern hade
  scenarier om sortering inte haft något naturligt ställe att anropa in
  på utan att gå via MockMvc/riktig HTTP, vilket hade suddat ut den
  gräns projektet redan håller isär. `WineControllerTest`
  (`@WebMvcTest` + `@MockBean WineService`) påverkades inte av valet -
  den mockar redan bort hela `WineService`, oavsett var logiken bor.
  Konsekvensen: `WineController` tolkar bara råa queryparametrar till
  typade värden (`Sorteringsfält`/`SorteringsRiktning`, bundna direkt
  via Spring, samma mönster som `WineType` redan binds i formulären) -
  `WineService.sök(...)` gör själva jobbet.
- **`Sorteringsfält` (enum, `application`-paketet) håller null-hantering
  och riktning strikt isär - en fälla som är lätt att gå i.** Varje
  konstant bygger sin comparator via en delad `medRiktning(fältvärde,
  stigandeOrdning, riktning)`-hjälpare som applicerar `Comparator.
  nullsLast(...)` **efter** att riktningen (stigande/fallande) redan
  avgjort om `stigandeOrdning` ska vara `.reversed()` eller ej - inte
  tvärtom. Ett vin utan pris/betyg satt ska alltid hamna sist i listan,
  oavsett om sorteringen är stigande eller fallande (verifierat av
  Gherkin-scenariot "Viner utan värde för det sorterade fältet hamnar
  sist, oavsett riktning"). Att i stället bygga
  `nullsLast(stigandeOrdning).reversed()` (nullsLast **innanför**
  reversed) hade fått null-värden att hoppa till **toppen** vid fallande
  sortering, eftersom `.reversed()` på en redan nullsLast-inslagen
  comparator vänder på hela jämförelsen - inklusive null-placeringen.
- **Betygsfälten (`EGET_BETYG`/`MUNSKANKARNA_BETYG`) sorteras via
  `Rating.ordinal()`, inte via etikettens bokstavsordning - en andra
  fälla i samma enum.** `Rating` deklareras i **fallande** betygsordning
  (`R20` bäst...`R6` sämst, se `Rating.java`), så `ordinal()` ger lägst
  tal för det bästa betyget. "Stigande sortering" ska betyda stigande
  betygsVÄRDE (sämst→bäst, dvs. `R6` före `R20`) - vilket är samma sak
  som **fallande** ordinal, därav `Comparator.comparing(Rating::ordinal).
  reversed()` som varje betygsfälts "stigande ordning". Verifierat av
  Gherkin-scenariot som jämför `R9`/`R10` specifikt (inte bara `R16`/
  `R19`) - deras etiketter ("9 (...)" och "10 (...)") sorterar **fel**
  bokstavsvis (`"10..."` < `"9..."` alfabetiskt, eftersom `'1'` har lägre
  teckenkod än `'9'`), så scenariot hade avslöjat en naiv
  strängjämförelse lika väl som det avslöjar en ordinal-utan-reversed-bugg.
- **Filtrering (byggd 2026-07-21) lade till `Sökkriterier`
  (Builder-baserad record) och ersatte `WineService.sök(Sorteringsfält,
  SorteringsRiktning)` med `sök(Sökkriterier)`.** Facetterna (vintyper,
  länder, regioner, underregioner) kombineras med OCH sinsemellan, ELLER
  inom en facett (tomt set = ingen begränsning för den facetten) - se
  `Sökkriterier`s klassdoc. Land/region/underregion-trädet för
  filterpanelens kryssrutor (`HärkomstNod`, `WineService.härkomstträd()`)
  härleds fräscht från samtliga viner vid varje anrop, **alltid
  obegränsat av aktivt filter** (statiska facetter, godkänt val i
  mockupomgången) - ingen uppslagstabell, matchar samma "fri text,
  normalisera inte i onödan"-linje som `location`/`grapes`. Ingen
  hierarki-medveten filterlogik behövs trots att kryssrutorna visas
  nästlat i UI:t - varje facett (land/region/underregion) filtrerar
  fullständigt oberoende av de andra, eftersom ett underregionsvärde i
  praktiken redan bara förekommer på viner från "rätt" land/region.
- **`<p class="traffrad">` ("Visar X av Y viner") måste ligga innanför
  `#vinlista`-fragmentgränsen, inte ovanför den - annars uppdateras inte
  träffantalet vid en htmx-driven filtrering/sortering.** Upptäcktes
  genom att faktiskt granska en Playwright-skärmdump av ett filtrerat
  resultat (2 av 4 kort visades, men texten sa fortfarande "Visar 4 av
  4") innan push - `WineControllerTest`s `skaVisaAntalTräffar` hade
  **inte** fångat buggen, eftersom den bara kollar att texten finns
  någonstans i svaret (`containsString`), inte var i DOM-trädet relativt
  fragmentgränsen. Allmän lärdom: ett `@WebMvcTest` som bara gör
  textmatchning mot hela svarskroppen kan missa den här sortens
  "rätt innehåll, fel del av sidan"-bugg - bara en verklig
  htmx-rundtur (eller ett test som specifikt kollar fragmentets
  avgränsning) avslöjar den.
- **Filterpanelens träd fälls nu ut automatiskt runt redan valda
  filter, och "Använd filter" döptes om till "Dölj filter" (fixat
  2026-07-21, båda felen rapporterade av användaren mot produktionen).**
  Grundorsaken till båda: `<form hx-trigger="change">` gör att en
  `<button type="submit">` i formuläret **inte** går via htmx alls -
  htmx lyssnar bara på "change" på det formuläret, så en
  knapptryckning utlöste en riktig sidladdning, vilken i sin tur
  fällde ihop alla `<details>`-noder i land/region/underregion-trädet
  (även de som täckte ett redan valt filter). `WineController.
  beräknaExpanderadeNoder(...)` löser det ena (räknar ut
  `expanderadeLänder`/`expanderadeRegioner`, mallen sätter `th:open`
  utifrån dem). Knappen fick dessutom `onclick="event.preventDefault();
  this.closest('details').removeAttribute('open')"` - projektets enda
  bit egenskriven JS utöver htmx, eftersom det inte finns något rent
  HTML/CSS-sätt att stänga ett `<details>`-element. Utan JS faller
  knappen tillbaka till en riktig submit (ofarligt tack vare
  `th:open`-fixen ovan) - motiverat eftersom kryssrutornas
  auto-tillämpning också kräver JS för att fungera alls.
- **Fritextsökning (byggd 2026-07-21/22, sista av de tre sök-/filter-/
  sorteringstilläggen) - `search_vector` är genererad Postgres-DDL, satt
  via `schema.sql`, inte via en manuell migrering.** Till skillnad från
  `db/migrations/2026-07-17-image-oid-to-bytea.sql` (manuellt
  engångsskript - se Datamodell) är `schema.sql` kopplad till
  `spring.sql.init.mode: always` och körs **automatiskt vid varje
  appstart**, inklusive i produktion (se separat punkt nedan om att
  filen numera droppar och återskapar kolumnen varje gång, inte bara
  `ADD COLUMN IF NOT EXISTS` som ursprungsversionen). Medveten
  avvikelse: den här migreringen är ren schema-DDL utan datamigrering
  (Postgres beräknar kolumnvärdet automatiskt, ingen befintlig data
  behöver flyttas/konverteras som oid→bytea-fallet krävde), så
  automatisk/idempotent körning är säker på ett sätt en datamigrering
  inte hade varit. **`spring.jpa.defer-datasource-initialization: true`
  krävs** för att `schema.sql` ska köras EFTER Hibernates
  `ddl-auto: update` skapat `wines`-tabellen, inte innan (annars
  kraschar `ALTER TABLE` mot en tabell som inte finns än). Bekräftat
  fungera mot en riktig, helt ny Postgres via `WineListResponsiveIT`
  (Testcontainers, `@SpringBootTest`) - den testar migreringen indirekt
  vid varje körning, inte bara vid en enda produktionsdeploy.
  `WineRepository.search(String)` implementeras olika i de två
  adaptrarna (`JpaWineRepository` mot riktig `tsvector`/`ts_rank`,
  `InMemoryWineRepository` mot en enkel skiftlägesokänslig
  delsträngsmatchning) - samma redan etablerade avvägning som
  `vin-persistens.feature` representerar för annan DB-specifik
  funktionalitet. Böjningsform-medvetenheten (stemming) verifierad
  manuellt mot en riktig Postgres: sökning på "kraftfull" hittade ett
  vin vars tasting notes bara innehöll "Kraftfulla".
  **Testfälla:** sökfältets `placeholder`-text ("Systembolagets
  beskrivning") kolliderade med en BEFINTLIG `WineControllerTest`-
  assertion (`not(containsString("Systembolagets beskrivning"))` i
  `skaDöljaProduktnummerOmBeskrivningSaknas`, som förutsatte att den
  frasen bara syns när ett visst vin faktiskt har fältet satt) -
  placeholder-texten förkortades till "Systembolaget" istället. Värt
  att komma ihåg: ny statisk UI-text (placeholders, etiketter) kan
  råka kollidera med `containsString`/`not(containsString(...))`-
  assertions som antog att en fras bara förekommer villkorligt.
- **Druvor (`grapes`) lades till i sökuttrycket (2026-07-22), viktat
  med namn/producent - `schema.sql` gick från "`ADD COLUMN IF NOT
  EXISTS`" till att DROPPA och ÅTERSKAPA `search_vector`-kolumnen (och
  dess GIN-index) vid varje appstart.** Postgres kan inte ändra en
  genererad kolumns uttryck på plats (inget `ALTER COLUMN ... SET
  EXPRESSION`), och `IF NOT EXISTS` hade gjort en redan existerande
  produktionskolumn permanent fastlåst vid sin gamla definition (utan
  druvor) - ALTER-satsen hade aldrig körts igen. Med drop+återskapa är
  `schema.sql` istället den enda sanningskällan för kolumnens FAKTISKA
  definition just nu; varje appstart konvergerar databasen mot filens
  innehåll, oavsett vad som fanns innan. Kostnaden (hela
  `search_vector` räknas om för alla rader, indexet byggs om) är
  försumbar för samlingsstorleken. Kom ihåg det här mönstret för
  framtida ändringar av `search_vector`s uttryck - `ADD COLUMN IF NOT
  EXISTS` fungerar bara för den ALLRA FÖRSTA gången kolumnen skapas.
- **Sökning ignorerar diakritiska tecken (WINE-7, 2026-07-24)** - en
  sökning på "albarino" hittar nu druvan "Albariño". Två separata
  fixar, en per `WineRepository`-adapter (se ADR 0007 för
  Postgres-sidans fulla motivering):
  - `JpaWineRepository`: ny textsökkonfiguration `swedish_unaccent` i
    `schema.sql` (kopia av `'swedish'` med `unaccent`-ordboken kedjad
    före `swedish_stem`), använd i både `search_vector`s uttryck och
    `plainto_tsquery(...)`-anropen. **Fälla undviken:** en vanlig
    `unaccent(text)`-funktion hade INTE fungerat direkt i
    `GENERATED ALWAYS AS`-uttrycket - Postgres kräver `IMMUTABLE` där,
    och `unaccent()` är bara `STABLE`. En namngiven textsökkonfiguration
    (`to_tsvector('swedish_unaccent', ...)`) räknas däremot som
    `IMMUTABLE` oavsett vilka ordböcker den kedjar internt - samma skäl
    till att `'swedish'` redan fungerade. Verifierat manuellt mot en
    fristående Postgres-container (inte bara att `schema.sql` kör utan
    fel, som `WineListResponsiveIT`/`vin-persistens.feature` redan
    bekräftar indirekt vid varje testkörning): en rad med
    `grapes = 'Albariño'` matchades faktiskt av
    `plainto_tsquery('swedish_unaccent', 'albarino')`, en rad med
    `grapes = 'Nebbiolo'` gjorde det inte.
  - `InMemoryWineRepository` (bara testkoden, se CLAUDE.md:s tidigare
    punkt om varför den beter sig enklare än Postgres-sökningen):
    normaliserar bort diakritiska tecken med `java.text.Normalizer`
    (NFD-normalisering + ta bort Unicode-kategorin `\p{M}`, kombinerande
    tecken) på både sökterm och fältvärden, utöver den befintliga
    skiftlägesnormaliseringen. Till skillnad från böjningsform-medvetenhet
    (stemming, se ovan - en genuint Postgres-specifik nyans som aldrig
    replikerades i den enkla adaptern) är diakritikborttagning billig
    och exakt att återskapa i Java, så här finns ingen anledning att
    låta adaptrarna bete sig olika - ett nytt Cucumber-scenario i
    `soka-viner.feature` (körs mot `InMemoryWineRepository`, inget
    Docker-krav) verifierar båda hållen.
- **Chips (byggt 2026-07-22) - vanliga `<a href>`, inte htmx.** En chip
  per aktivt filter-/sökvärde, med en borttagningslänk
  (`WineController.Sökvy.urlUtan(facett, värde)`, `UriComponentsBuilder`)
  som bygger om hela URL:en minus det enskilda värdet. Medvetet INTE
  htmx-drivet: en borttagning måste uppdatera hela verktygsraden
  (kryssrutor, sökfält), inte bara `#vinlista`-fragmentet en htmx-swap
  annars hade varit begränsad till - en vanlig sidladdning garanterar
  att båda är synkade. Byggd i `WineController`, inte `WineService` -
  ren presentationslogik utan Gherkin-relevans.
- **`DELETE /wines/{id}` (`taBortVin`) kraschade när `chips`/
  `antalTotalt` blev ovillkorliga referenser i `#vinlista`-fragmentet
  (fångat av `NärEttVinTasBort`-testsviten, inte manuellt).**
  `antalTotalt` hade redan varit en tyst lucka sedan filtreringsomgången
  (ett `th:text` på `null` ger bara tom text, inget fel) - men
  `chips.isEmpty()` anropat på `null` gav en `SpelEvaluationException`
  och kraschade hela borttagningen. Fixat genom att sätta båda i
  `taBortVin` också (`chips` tom lista, `antalTotalt` = antalet
  kvarvarande viner).
  **Begränsningen (borttagning återställde till standardvyn) fixad
  2026-07-22, på användarens begäran.** `WineController.
  fyllIVinlistaModell(...)` (samma `@RequestParam`-uppsättning som
  `vinkällare`, samma sök-/filter-/sorteringspipeline) delas nu av
  både `GET /` och `DELETE /wines/{id}`, så `taBortVin` gick från att
  bara anropa `wineService.listWines()` till att anropa
  `wineService.sök(kriterier)` precis som `vinkällare`. Löst utan
  `hx-include`: "Ta bort"-knapparnas `hx-delete`-URL byggs nu med
  `@{...}`-länkuttrycket och alla sju queryparametrarna
  (`sok`/`sortera`/`riktning`/`wineType`/`country`/`region`/
  `subregion`) direkt i `vinkallare.html`, istället för att förlita sig
  på att läsa in värden från formuläret vid klicktillfället - enklare
  och fungerar oavsett var knappen ligger i DOM:et relativt formuläret.
  Thymeleafs `@{...}` expanderar `Set`-värden till upprepade
  queryparametrar automatiskt (`wineType=RED&wineType=WHITE`), samma
  mekanism som redan användes för `GET /`. Verifierat med Playwright
  mot en riktig körande app: ta bort ett rött vin medan "Rött"-filtret
  är aktivt lämnar filtret aktivt och det kvarvarande röda vinet synligt
  efteråt, det borttagna vinet försvinner, ett vitt vin förblir
  exkluderat.
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
  vertikalt stack med `<dl>`-fältetiketter till en tvådelad layout:
  `.vinkort-topp` är en flex-rad med en smal bildkolumn
  (`.vinkort-bildyta`, `flex: 0 0 5.5rem`) och en textkolumn, medan
  betygen, `<details>` och `.vinkort-fot` (Redigera/Ta bort) ligger som
  egna block **direkt under** `.vinkort` (utanför `.vinkort-topp`) och
  därmed spänner hela kortets bredd. Den uppdelningen kom i en andra
  omgång, efter att användaren påpekade att en flaskbild ofta slutar
  ungefär vid druvor-raden - att låta betyg/Detaljer/knapparna ligga
  kvar i den smala textkolumnen bredvid bilden slösade bort utrymmet
  under bilden. De flesta fälten (producent, namn+årgång, ursprung,
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
  **Redigera/Ta bort flyttade in i Detaljer, högerjusterade (2026-07-19,
  gäller både tabell- och kortvyn - till skillnad från de tidigare
  kortspecifika omgångarna).** Låg tidigare alltid synliga i
  översikten: en egen `<td>`/kolumn i tabellraden, `.vinkort-fot` som
  en vänsterjusterad kolumn under kortet. Ligger nu sist i den infällda
  `<details>`-sektionen i en delad `.detalj-atgarder`-`<div>`
  (`display: flex; justify-content: flex-end`), återanvänd i både
  tabellens och kortets Detaljer istället för separata layouter.
  Eftersom hela åtgärdskolumnen försvann ur tabellens `<thead>` och
  huvudrad sänktes `colspan` på detaljraden från `14` till `13` - en
  lätt fälla att missa om man bara ändrar en av de två platserna.
  **Fälla att komma ihåg för nästa kolumnändring:** `colspan` måste
  alltid matcha exakt antalet `<th>` i `<thead>`, annars blir
  detaljradens `<td>` fel bred (för smal om `colspan` är för lågt, eller
  sträcker sig utanför tabellen om det är för högt) - lätt att missa
  eftersom det inte ger något kompilatorfel, bara ett tyst
  layoutproblem som bara syns visuellt.
  **Detaljer-fältens ordning omarbetad, scopead till bara kortvyn
  (2026-07-19).** Ny ordning: Inköpsdatum, Pris, Plats, Varför köpt,
  Tasting notes, Systembolagets beskrivning, Munskänkarnas bedömning,
  Annan referens (oförändrad sistplacering; Systembolagets
  produktnummer försvann ur ordningslistan när det slogs ihop med
  beskrivningsraden, se nästa punkt). De fyra sista (Varför köpt,
  Tasting notes, Systembolagets beskrivning, Munskänkarnas bedömning)
  visar värdet under etiketten istället för bredvid - Varför köpt fick
  samma behandling i en uppföljande justering samma dag efter att
  användaren påpekade att den var inkonsekvent utelämnad från de tre
  andra som redan staplades. Medvetet **inte** löst genom att ändra
  `detaljfalt`-fragmentets DOM-ordning eller duplicera det till en
  kort-specifik variant - det hade återinfört exakt den
  dubbleringsrisk fragmentet ursprungligen skulle undvika. Istället
  fick varje `dt`/`dd`-par en `fd-*`-klass (`fd-inkopsdatum`, `fd-pris`,
  `fd-plats`, `fd-varfor-kopt`, `fd-tasting`, `fd-sb-beskrivning`,
  `fd-munskankarna`, `fd-annan-referens`), och CSS `order` sätts på
  dessa klasser **scopeat under `.vinkort dl`** (inte globalt) -
  fragmentets faktiska DOM-ordning i källkoden är alltjämt den
  ursprungliga (Plats först). Tabellvyns `.detaljlista-bred` har ingen
  matchande `order`-regel och behåller därför sin egen dokumentordning
  helt opåverkad, trots att båda vyerna renderar exakt samma
  `dt`/`dd`-element via samma `th:insert`-anrop. De fyra staplade
  fälten kombinerar `order` med `grid-column: 1 / -1` - att låta både
  `dt` och dess `dd` spänna hela grid-bredden tvingar
  auto-placeringsalgoritmen att lägga dem på varsin egen rad (`dt`
  följt av `dd` direkt under), vilket ger stapling utan någon extra
  `<span>`-uppdelning av label/värde (till skillnad från
  `.betyg-label`/`.betyg-varde`-mönstret som användes för betygsraderna
  tidigare, där käll-HTML:en själv behövde två separata element).
  **Annan referens fick samma stapling (2026-07-20)**, efter att
  användaren påpekade att den - liksom Varför köpt i föregående
  justering - var inkonsekvent kvar i sida-vid-sida-läget. `fd-annan-
  referens { order: 90 }` fick `grid-column: 1 / -1` tillagt, exakt
  samma mönster som de fyra övriga staplade fälten - inga andra
  ändringar behövdes.
  **Systembolagets produktnummer slogs ihop med beskrivningsraden
  (2026-07-19), i BÅDA vyerna - till skillnad från
  ordningsjusteringen/staplingen ovan, som bara gäller kortvyn.**
  `fd-sb-nummer`-raden (egen `dt`/`dd`) togs bort helt.
  `fd-sb-beskrivning`s `dt` bygger nu sin text villkorligt: `th:text=
  "${vin.systembolagetProductNumber != null} ? |Systembolagets
  beskrivning (${vin.systembolagetProductNumber})| : 'Systembolagets
  beskrivning'"`. Eftersom det här är en ändring av vad
  `detaljfalt`-fragmentet faktiskt renderar (inte en CSS-scopead
  layoutskillnad som ordning/stapling), slår den igenom i både
  tabellens `.detaljlista-bred` och kortets `.vinkort dl` - det finns
  ingen `.vinkort`-scopead regel att gömma sig bakom här. **Om
  `systembolagetDescription` är `null` visas produktnumret inte alls**,
  även om det är satt - `th:if="${vin.systembolagetDescription !=
  null}"` styr hela `dt`/`dd`-paret, och utan en beskrivning finns
  ingen etikett att fästa parentesen på. Medvetet vald avvägning
  (dokumenterad i README, inte bara en bugg som råkade hända) - om
  produktnummer-utan-beskrivning visar sig vara ett verkligt
  datamönster är det en enkel ändring att lägga till en fallback-rad.
  **Tabellvyns designomgång (2026-07-19/20) - `<table>`,
  `.detaljlista-bred`, `<tr class="detaljrad">`/`colspan` och
  `vinbild-tabell` (allt beskrivet ovan) är alltså numera historik, inte
  gällande kod.** Styrd av en PNG-mockup (`Vinlista.png`) och en
  Artifact-jämförelse som itererades i flera omgångar innan bygget:
  dämpade labels (mindre, grå, `font-weight: 400` istället för fetstil),
  betygsraden flyttad upp bredvid bilden, fältordning, labels linjerade
  på samma höjd, och till sist fasta betygskolumnbredder. Beslutet var
  uttryckligen **ingen infälld Detaljer på desktop** - jämfört mot
  kortvyns "visa lite, fäll ut resten"-modell är tabellvyn nu "visa
  allt direkt", vilket avslöjade att `otherReference` ("Annan
  referens") aldrig visades någonstans i listan tidigare (varken i
  gamla tabellen eller kortvyns Detaljer) - ett dolt hål i
  fältexponeringen som bara syntes när kravet blev "visa allt".
  - `#vinlista-tabell` innehåller nu `.vinkort-bred`-kort, inte en
    `<table>` - `id`:t/klassnamnet är kvar (CSS-brytpunkten och
    `WineListResponsiveIT` pekar redan på det), men strukturen är en
    helt annan. `vk-`-prefixet på de nya klasserna är medvetet skilt
    från kortvyns `vinkort-`-prefix - de två vyerna har olika layout
    (fyra kolumner mot en smal kolumn), och samma klassnamn med olika
    betydelse i olika vyer hade varit förvirrande.
  - **Fyra kolumner delas av `.vk-topp`/`.vk-info-rad`/`.vk-text-rad`**
    via samma `grid-template-columns: 6rem 1fr 18rem 18rem`, så
    Inköpsdatum hamnar under bilden, Pris under textblocket, Varför
    köpt under Munskänkarna och Plats under Eget betyg. **Varje fält
    har ett explicit `grid-column`** (`.vk-inkopsdatum { grid-column:
    1 }` osv.) - utan det skulle CSS Grids auto-placering fylla nästa
    lediga cell i dokumentordning, så om t.ex. ett vin saknar
    `purchaseDate` (Inköpsdatum) skulle Pris hoppa in i kolumn 1
    istället för kolumn 2 och hela kolumnjusteringen brytas för just
    det vinet. Samma fälla som `fd-*`-klassernas `order`-lösning
    ovan undvek på ett annat sätt (där gällde det ordning, inte
    kolumnplacering).
  - **Betygsraden (Vivino/Munskänkarna/Eget betyg) är en egen
    `grid-row: 2`** i `.vk-topp`, bredvid bilden (`.vk-bildyta`, som
    spänner `grid-row: 1 / 3` och stretchar till samma höjd som
    text+betyg tillsammans). Alla tre labels börjar därför på exakt
    samma höjd, oavsett hur många rader respektive värde råkar
    radbryta till - samma princip som `fd-*`-klassernas
    grid-row-lösning i kortvyn, fast tillämpad direkt i strukturen
    istället för via `order`.
  - **`.vk-munskankarna`/`.vk-egetbetyg` har fast bredd (`18rem`, inte
    `fr`)** - de måste rymma det längsta möjliga betygsvärdet (någon av
    de 29 Rating-etiketterna; längst är `"12,5 (12 - 14,5 Bra till
    mycket bra vin)"` och liknande, ~41 tecken) oavsett vilket av de
    två fälten som råkar ha ett långt värde - det kan lika gärna vara
    Eget betyg som Munskänkarna. Verifierat lokalt med båda fälten
    satta till den längsta etiketten samtidigt (worst-case, inte bara
    det exempel användaren råkade visa i sin mockup).
  - **Sidan fick höjas i bredd för att detta skulle få plats:** `body`s
    `max-width` gick från `48rem` till `70rem`, och
    `@media`-brytpunkten mellan bred kortvy och mobil kortvy gick från
    `640px` till `960px`. De fasta 18rem-kolumnerna krymper aldrig, så
    under ~960px skulle layouten svämma över (horisontell scroll) om
    inte mobilvyn tog över istället - verifierat manuellt vid 900px
    (faller tillbaka till kortvyn utan överflödning) och 1280px (breda
    kort, inga betygsvärden radbryter).
  - Redigera/Ta bort ligger direkt i `.vk-topp`-kortet
    (`.detalj-atgarder`, samma klass som kortvyns infällda variant
    återanvänder) - inte bakom något klick, till skillnad från kortvyn.
  - **Testkonsekvens:** `WineListResponsiveIT`s
    `skaDöljaRedigeraOchTaBortTillsDetaljerFällsUtPåDesktop` byttes mot
    `skaVisaRedigeraOchTaBortDirektPåDesktop` (inget klick behövs
    längre) plus `skaVisaAllaFältDirektPåDesktopUtanAttFällaUtNågot`;
    readonly-testet tappade sitt `"Detaljer"`-klick av samma skäl.
    `WineControllerTest` fick `skaRenderaBredaKortMedAllaFältSynliga`,
    som bland annat verifierar att `<table>` och `vinbild-tabell`
    **inte** längre förekommer i den renderade HTML:en.
  **Bildens storlek/position justerad i fyra omgångar (2026-07-20)**,
  efter att användaren tyckte den var onödigt stor och sedan ville ha
  under- och till sist överkanten linjerad. Kolumnbredden (`6rem`) i
  `grid-template-columns` rördes **inte** i någon av omgångarna - den
  delas med Inköpsdatum i `.vk-info-rad`, som behöver bredden för att
  `"2026-04-18"`-liknande datumvärden inte ska radbryta. Istället
  begränsades bilden/platshållaren själv: `.vk-bildyta img`/
  `.vk-bildplatshallare` fick ett `max-width`/`max-height` (mindre än
  sin 6rem-kolumn, lämnar tomrum till höger) istället för
  `width: 100%; height: 100%`.
  1. Första försöket: `max-width: 3.5rem; max-height: 5rem`,
     `grid-row: 1` (bara textblockets rad, `align-self: start`) - med
     den ursprungliga `grid-row: 1 / 3`-regeln kvar hade en liten bild
     bara lämnat ett stort tomt utrymme under sig ner till betygsraden
     istället för att faktiskt bli mindre som helhet. **Fälla
     undviken:** att bara krympa `max-width`/`max-height` utan att
     också ändra `grid-row` hade gjort bilden mindre men lämnat
     kolumnen lika hög som förut. Visade sig vara för litet - användaren
     tyckte kortvyns bildstorlek (`.vinbild-kort`, `.vinkort-bildyta {
     flex: 0 0 5.5rem }`, `max-height: 12rem`) såg bättre ut.
  2. Andra omgången: `max-width: 5.5rem; max-height: 8rem` - matchar
     kortvyns kolumnbredd men med lägre maxhöjd, eftersom `.vk-topp`s
     textrad (bara producent/namn/ursprung/typ/druvor) är kortare än
     hela kortvyns kort (som även innehåller betyg och infälld Detaljer
     i samma flöde). Fortfarande upplevt som lite för litet, och
     `align-self: start` gav ingen särskild linjering av underkanten.
  3. Tredje omgången: `.vk-bildyta` bytte tillbaka till
     `grid-row: 1 / 3` (som i den allra första, "för stora" versionen)
     men med `align-self: end` istället för `stretch`, plus
     `max-width: 6rem; max-height: 9rem`. **Skillnaden mellan `stretch`
     och `end` är kärnan i lösningen:** `stretch` (originalversionen)
     fyller hela den spända ytan oavsett hur hög den är - det var vad
     som gjorde bilden "för stor" från början. `end` positionerar en
     bild med sin egen begränsade storlek vid nederkanten av samma yta
     - bilden förblir liten (styrd av `max-width`/`max-height`) men dess
     underkant hamnar ändå i linje med betygsradens underkant (samma
     höjd som Vivino-värdet), eftersom båda ligger i samma grid-area.
  4. **Fjärde omgången: både överkant och underkant.** Användaren
     ville att bilden även skulle linjera mot producentnamnets överkant,
     inte bara mot Vivino-värdets underkant. Att träffa **båda** kanterna
     samtidigt går bara med `align-self: stretch` tillbaka - `end` (steg
     3) och `start` (steg 1) kan bara träffa en kant i taget, eftersom en
     begränsad storlek som inte fyller hela ytan alltid lämnar tomrum
     någonstans. `.vk-bildyta img`/`.vk-bildplatshallare` gick från
     `max-height` till `height: 100%` (fortsatt `max-width: 6rem` för
     bredden) - `object-fit: contain` skalar innehållet proportionerligt
     utan distorsion, men ytan (och därmed hur hög bilden faktiskt blir)
     växer nu med textmängden, en medveten avvägning för att klara båda
     kanterna samtidigt - i praktiken samma CSS som den allra första
     "för stora" versionen i steg 3 ovan. **Ny fälla som dök upp här:**
     `<a>`-taggen som omsluter `<img>` när vinet har en bild är
     `display: inline` som standard, vilket saknar en egen resolverbar
     höjd - `height: 100%` på `<img>` gick då inte igenom kedjan
     korrekt. Fixat med `.vk-bildyta a { display: block; height: 100%
     }`. Behövdes inte i steg 1-3, eftersom `max-height` i rem-enheter
     inte är beroende av att föräldraelementet har en resolverbar
     procentuell höjd.
  5. **Femte omgången: `object-position: center bottom`.** Steg 4
     verifierades bara mot "Ingen bild"-platshållaren (en vanlig
     `<div>` utan eget bildförhållande, som trivialt fyller hela sin
     `height: 100%`-box) - **inte mot en riktig uppladdad bild.**
     Användaren upptäckte mot den riktiga deployen att en riktig
     flaskbild inte alls följde underkanten: `object-fit: contain`
     centrerar bildens innehåll inom sin box som standard
     (`object-position: 50% 50%`) när bildens eget höjd/bredd-
     förhållande inte fyller hela den spända ytan, vilket lämnade
     tomrum både ovanför **och** under bilden. **Testfälla att komma
     ihåg:** platshållaren och en riktig bild beter sig olika med
     `object-fit`/`object-position` just eftersom bara den senare har
     ett eget, fast bildförhållande - verifiera alltid mot en faktiskt
     uppladdad bild (t.ex. genererad lokalt med Pillow,
     `python -m pip install pillow`, om ingen riktig flaskbild finns
     till hands), inte bara mot "Ingen bild"-fallet, när en ändring rör
     `.vk-bildyta img`. Fixat med `object-position: center bottom` -
     tvingar `contain` att lägga eventuellt överskottsutrymme högst upp
     istället för att dela det mellan topp och botten. **"Kvarstående
     avvägning" som dokumenterades här var feldiagnostiserad - se steg 6,
     det var inte bara ett litet tomrum ovanför.**
  6. **Sjätte omgången: `position: absolute` - den verkliga boven.**
     Användaren rapporterade (ny skärmdump) att underkanten fortfarande
     inte alignade efter steg 5 - efter hård refresh och test i en annan
     webbläsare (uteslöt cache/deploy-fördröjning) gick det att
     återskapa lokalt, men bara med **både** en smal/hög testbild
     (200×1000, smalare än steg 4/5:s 300×900) **och** ett vin med
     **lite text** (kort producent/namn/ursprung, inga druvor) samtidigt.
     Med lite text är textens/betygets egna naturliga höjd liten, så
     bildens naturliga bildförhållande-höjd blir lättare den dominerande
     faktorn - och en riktig, smal/hög flaskbild kunde då tvinga upp
     **hela rad 1+2:s höjd** till bildens egen höjd, trots `height:
     100%` på `<img>` (en procentandel som "borde" vara ofarlig).
     Grid-/flex-item har `min-height: auto` som standard, vilket låter
     deras eget innehålls naturliga storlek räknas in i hur höga
     "auto"-raderna blir. **Ett första försök att nollställa detta med
     `min-height: 0` på `.vk-bildyta` räckte INTE** - verifierat med
     samma stress-test (smal/hög bild + lite text) som avslöjade buggen;
     den kvarstod oförändrad efter den fixen. Den robusta lösningen: ta
     bilden helt ur det normala dokumentflödet med `position: absolute`
     (`inset: 0` fyller hela `.vk-bildyta`s yta; `.vk-bildyta` fick
     `position: relative` som positioneringskontext åt `<a>`/`<img>`/
     platshållaren). Absolutpositionerade element kan **aldrig** bidra
     till sin förälders/grid-radens automatiska storleksberäkning,
     oavsett bildens eget bildförhållande - till skillnad från
     `min-height: 0`, som bara justerar en tröskel (`min-content`-bidrag)
     men inte helt kopplar bort bidraget till track-sizing-algoritmen.
     `max-width: 6rem` på `<img>`/platshållaren blev överflödig och togs
     bort - `.vk-bildyta`s grid-kolumn är redan exakt `6rem`, och
     `inset: 0` fyller den bredden ändå.
     **Lärdom om testmetodik, viktig för framtida `.vk-bildyta`-ändringar:**
     omgång fyra/fem verifierades bara med EN bildproportion (300×900)
     på ett vin med MYCKET text - inte extremt nog för att avslöja
     buggen. Verifiera alltid med **både** en ovanligt smal/hög
     testbild **och** ett vin med minimal text samtidigt (den
     kombination som gör bildens eget bidrag till radhöjden som störst
     relativt textens) - inte bara mot "Ingen bild"-platshållaren eller
     en enda "typisk" bild/textkombination.
  Verifierat manuellt vid 1280px efter alla sex omgångarna, sista
  gången med både en riktig (lokalt genererad, 200×1000) flaskbild och
  ett textfattigt vin, plus regressionskoll av "Ingen bild"-fallet.
  **Kortvyns (mobil) label-stil enhetligad med de breda korten
  (2026-07-20).** `.vinkort-betyg .betyg-label` (tidigare odämpad,
  ingen egen font-stil alls) och `.vinkort dt`/`.vinkort dd` (tidigare
  `font-weight: bold` på `dt`) fick samma deklarationer som
  `.vk-label`/`.vk-value` (`font-size: 0.78rem; color: #767676;
  font-weight: 400` för labels, `font-size: 0.95rem; color: #1a1a1a;
  line-height: 1.4` för värden) - kopierade deklarationer på egna
  klasser/selektorer, inte samma klassnamn återanvänt, eftersom
  kortvyn behåller sina egna `betyg-label`/`dt`/`dd`-element (den
  delade `detaljfalt`-fragmentets `fd-*`-klasser för ordning/stapling
  påverkas inte av detta). Verifierat manuellt vid 375px med Detaljer
  uppfälld.

## Säkerhet

- **Hela appen kräver HTTP Basic-inloggning** via `SecurityConfig`
  (`.anyRequest().authenticated()` som fallback) - till skillnad från
  roombooking (som bara skyddade `/admin/**`) finns här inget legitimt
  anonymt användningsfall. Appen har ingen separat publik läsvy, så
  varje route låter i grunden en besökare ändra vinsamlingen - och
  appen var redan nåbar från nätet innan detta beslut togs. ADMIN-
  kontot heter `admin`, lösenord från `winecellar.admin.password`/
  miljövariabeln `WINECELLAR_ADMIN_PASSWORD` (default `admin` bara
  lokalt).
- **READONLY-kontot (byggt 2026-07-19):** `readonly`/`readonly` - både
  användarnamn och lösenord hårdkodade i `SecurityConfig` (inte en
  miljövariabel som admin-lösenordet), eftersom kontot medvetet är
  tänkt att vara ett känt, delbart "titta men inte ändra"-konto, inte
  en hemlighet. Får GET `/` och GET `/wines/{id}/bild` (`hasAnyRole
  ("ADMIN", "READONLY")`), men nekas allt annat: GET `/wines/nytt` och
  GET `/wines/{id}/redigera` (formulärsidorna) är `hasRole("ADMIN")`,
  liksom POST `/wines`, POST `/wines/{id}/redigera` och DELETE
  `/wines/{id}`. Formulärsidornas GET-routes är medvetet också
  admin-bara, inte bara POST/DELETE - annars går det att komma åt
  "lägg till"/"redigera"-formuläret genom att gissa på URL:en även om
  länken är dold i UI:t (se nästa punkt). `WineController.vinkällare`/
  `taBortVin` sätter en `kanRedigera`-modellattribut
  (`Authentication.getAuthorities()` innehåller `ROLE_ADMIN`) som
  `vinkallare.html` använder för att dölja "Lägg till vin"-länken och
  varje vins `.detalj-atgarder`-block (Redigera/Ta bort) för READONLY -
  **bara ett extra UI-lager**, inte den faktiska åtkomstkontrollen; den
  sitter i `SecurityConfig` och gäller oavsett vad UI:t visar.
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

## Etikettskanning (LLM)

**Byggd 2026-07-24 (WINE-5).** Appens första beroende av en extern
tjänst (Anthropic) utöver Postgres - se
[ADR 0012](docs/adr/0012-label-scanning-llm-integration.md) för
motiveringen bakom de arkitektoniska valen (port/adapter,
`RestClient` istället för den officiella SDK:n, konfiguration via
miljövariabler, testuppdelningen mellan Cucumber/MockMvc/Playwright).
Punkter värda att komma ihåg utöver ADR:n:

- **`LabelInterpreter.interpret(...)` returnerar `Optional<InterpretedLabel>`,
  inte ett värde-objekt med en egen "misslyckades"-flagga.** `empty()` =
  total misslyckning (nätverksfel, LLM-fel, eller alla fem fälten blev
  `null`) - ett `InterpretedLabel` med enstaka `null`-fält är fortfarande
  ett LYCKAT resultat (bara namnet gick t.ex. att läsa). Att blanda ihop
  dessa två hade gjort "bara namnet tolkades"-scenariot (WINE-5) omöjligt
  att skilja från ett totalt misslyckande.
- **`LabelInterpretationService.interpretedFields()` räknas ut från
  vilka av de fem fälten som är icke-`null` i svaret** - ingen separat
  boolesk flagga per fält behövdes, eftersom country/region (som FÅR
  härledas) och name/producer/vintage (som INTE får det) ändå bara har
  två tillstånd ur markeringens synvinkel: "kom med i svaret" eller
  "gjorde det inte".
- **Etikettskanningens formulärfält döljs helt vid redigering
  (`th:if="${wine.id == null}"` i `vin-formular.html`)** - att skanna om
  ett redan sparat vin är inte en del av WINE-5:s scope.
- **`th:classappend`, inte `th:class`, för `tolkat-falt`-markeringen** -
  `th:class` hade skrivit över hela `class`-attributet, vilket är
  ofarligt just nu (inga andra klasser sätts på de fem fälten) men
  `th:classappend` är den robusta varianten om ett fält någon gång får
  en egen basklass.
- **Klientsidans nedskalning (Canvas, före uppladdning) är projektets
  första mer-än-triviala JavaScript** - `DataTransfer`/`File`-tricket
  för att ersätta `<input type="file">`s valda fil efter nedskalning är
  standardmönstret för detta, stöds av alla moderna webbläsare som
  redan krävs för `capture="environment"`.
- **`LabelScanFormIT` (Playwright) mockar `LabelInterpreter`
  (porten), inte `LabelInterpretationService`** - den riktiga tjänsten
  körs alltså i det testet, till skillnad från `WineControllerTest`
  (`@WebMvcTest`) som mockar `LabelInterpretationService` direkt
  eftersom den testet ändå bara stubbar bort hela applikationslagret.
- **Playwrights `setInputFiles(...)` med en riktig, avkodningsbar
  1x1-PNG (samma testbild som `WineRowWriterTest`), inte godtyckliga
  bytes** - klient-JS:en laddar bilden i ett `Image`-element för att
  läsa dess bredd/höjd inför nedskalningen, vilket kräver att
  webbläsaren faktiskt kan avkoda testbilden.

**Statusinfo under skanningen (byggt 2026-07-24, WINE-8).** Två separata
statusmeddelanden, inte ett:
- **"Analyserar etikett..."** sätts synkront i JS direkt när filen
  väljs (`vin-formular.html`), INNAN Canvas-nedskalningen eller
  nätverksanropet ens startar. Fungerar utan htmx/fetch eftersom hela
  sidan navigerar bort när formuläret skickas in - meddelandet hinner
  synas hela väntetiden fram till att svaret (den omrenderade sidan)
  kommer tillbaka, exakt samma mekanism som gör att en vanlig
  sidladdning fungerar här över huvud taget.
- **"Fyllde i: ..."** byggs server-side i `WineController.
  interpretedFieldLabels(...)` från en FAST fältordning
  (`INTERPRETED_FIELD_ORDER`), inte `interpretedFields`s egen
  iterationsordning (ett `HashSet`) - annars hade meddelandet blivit
  icke-deterministiskt mellan körningar.
- **Testfälla:** `th:text` på ett separat `<span>` inuti `<p>`-taggen
  (`Fyllde i: <span th:text="...">...</span>.`) bryter sönder texten
  med en tagg mitt i - `content().string(containsString("Fyllde i:
  Namn."))`-style MockMvc-assertions matchar då INTE, eftersom de
  jämför mot den råa HTML-strängen, inte upprenderad DOM-text. Löst med
  Thymeleafs literalsubstitution (`th:text="|Fyllde i: ${...}.|"`) på
  hela `<p>`-elementet istället, som ger en sammanhängande textnod.
- **Playwright-testet för "Analyserar etikett..." kräver en konstgjord
  fördröjning i den mockade `LabelInterpreter`** (`Thread.sleep(800)`
  i `thenAnswer(...)`) - annars hinner mock-svaret komma tillbaka och
  sidan navigera bort innan assertionen läser statusraden, eftersom
  testbilden är en trivial 1x1-PNG utan någon riktig nätverksfördröjning
  att luta sig mot.

## Flera användare (multi-user) - påbörjat 2026-07-24

**Beslutet är skrivet ner som [ADR 0013](docs/adr/0013-multi-user-accounts.md)
INNAN implementationen, ovanligt för det här projektet** (som annars
skriver ADR:er i samband med att koden landar, se `docs/adr/README.md`s
egen beskrivning av mönstret) - motiverat av att omställningen är stor
nog att sträcka sig över flera separata stories (WINE-10 till WINE-17 i
YouTrack, WINE-projektet). Att fastslå vägvalen skriftligt i förväg
minskar risken att en session som senare plockar upp en enskild story
måste återupptäcka avvägningarna ur koden.

Kort sammanfattning av besluten (fullständig motivering i ADR:n): öppen
självregistrering, formulärbaserad inloggning med session (ersätter HTTP
Basic från [ADR 0009](docs/adr/0009-whole-app-http-basic-auth.md) helt -
CSRF slås på igen, htmx-formulären behöver en CSRF-token), varje
användares vinlista är helt privat (ingen delning, READONLY-rollen
försvinner), en ny `User`-entitet plus `owner_id`-kolumn på `wines`, och
befintliga produktionsviner (~30 st) knyts till det första riktiga
kontot via en engångsmigrering (samma mönster som tidigare
engångsmigreringar, se `db/migrations/`). Import/export via webben blir
en separat, senare fas - river upp
[ADR 0010](docs/adr/0010-excel-tool-standalone-module.md) (POI blir ett
runtime-beroende i huvudappen); bilder hanteras då via en mappväljare i
webbläsaren (`webkitdirectory`), inte en lokal serversökväg som dagens
`Bildmatchare`/`WINECELLAR_LOCAL_IMAGE_FOLDER` förutsätter.

Stories i YouTrack, länkade med "depends on" i den tänkta byggordningen:
WINE-9 (den här ADR:n) → WINE-10 (datamodell: `User`/`owner_id`) →
WINE-12 (formulärinloggning) → WINE-11 (registrering) och WINE-13
(scopead vinlista) → WINE-16/WINE-18 (testinfrastruktur) → WINE-14
(dataisolering, eget acceptanstest) → WINE-15 (ta bort ADMIN/READONLY,
medvetet sist av säkerhetsskäl - annars riskerar man att låsa sig ute
innan den nya inloggningen är bevisat fungerande) → WINE-17
(produktionsmigrering, sist).

**WINE-10 byggd (2026-07-24): `User`-entitet + `owner_id`-koppling.** Ny
`User`/`User.UserId` (`domain/`), tunt precis som `Wine`/`WineId`, en
`UserRepository`-port och två adaptrar (`JpaUserRepository`/`UserEntity`
mot en ny `users`-tabell, `InMemoryUserRepository` som testdubblett,
samma mönster som `WineRepository`). `WineEntity` fick en
`@ManyToOne owner`-relation mot `owner_id` (nullable, `Hibernate ddl-auto:
update` skapade både tabellen och FK-constraintet automatiskt - inget
behövde läggas i `schema.sql`, verifierat manuellt mot en lokal Postgres
med `\d users`/`\d wines`). Medvetet inget annat kopplat än så här -
varken `Wine`-domänobjektet eller `WineService`/`WineController` vet
ännu att ägaren finns; själva scopingen är WINE-13.

**Deploy-fälla upptäckt 2026-07-24/25, kopplad till WINE-10:s
`owner_id`-kolumn.** Produktionsdeployen kraschade med
`org.postgresql.util.PSQLException: cannot alter type of a column used
by a generated column` (`grapes`, blockerad av `search_vector`). Orsak:
`WineEntity.grapes`/`tastingNotes`/`systembolagetDescription`/
`munskankarnaReview` har haft `@Column(columnDefinition = "text")` ett
tag, men Hibernates `ddl-auto: update` hade aldrig tidigare haft ett
skäl att röra `wines`-tabellen och därför aldrig försökt bredda dem från
`varchar(255)` till `text` förrän `owner_id`-kolumnen gav den ett skäl
att göra en fullständig kolumngenomgång av tabellen - Postgres tillåter
inte den breddningen så länge `search_vector` refererar till kolumnen,
och `ddl-auto: update` kan inte lätta på det (samma sorts begränsning
som oid→bytea-migreringen redan stötte på). Löst med en engångsmigrering
(`db/migrations/2026-07-25-drop-search-vector-before-column-widen.sql`)
som bara droppar `search_vector` - `schema.sql` återskapar den
automatiskt vid nästa lyckade appstart, eftersom den ändå körs (drop +
återskapa) vid VARJE uppstart. Kördes manuellt mot produktionsdatabasen
via en engångs-`docker run postgres:16 psql ...`-container (användaren
saknade DBeaver/psql lokalt) - ingen lokal installation behövdes eftersom
Docker redan fanns på maskinen. **Lärdom:** `ddl-auto: update` kan dölja
den här sortens konflikt i flera deployer på rad om inget annat ger
Hibernate skäl att röra samma tabell - dyker upp överraskande långt efter
att den bakomliggande `columnDefinition`-ändringen gjordes, inte vid det
tillfället.

**Uppföljning (2026-07-25): den första migreringen var inte tillräcklig -
samma "cannot alter type"-fel kom tillbaka på en senare deploy, trots att
en deploy däremellan hade lyckats starta.** Grundorsaken var mer
lömsk än först trott: Hibernates schemamigrering kör flera ALTER-satser
i samma transaktion, i en ordning som styrs av `HashMap`-iteration (se
`SchemaManagementToolCoordinator.process`) - inte garanterat stabil
mellan körningar. Om `grapes`-breddningen misslyckas (blockerad av
`search_vector`, som schema.sql annars hade återskapat EFTER Hibernate)
kan Postgres transaktionsavbrott antingen tystas bort av Hibernates
icke-fatala DDL-felhantering (appen startar ändå, breddningen uteblir
tyst) eller få en SENARE sats i SAMMA transaktion att också fejla på
ett sätt som väl kraschar hela starten - vilket av de två beror på
slumpmässig satsordning. Med andra ord: den "lyckade" deployen (WINE-12)
hade sannolikt bara tur, `grapes` blev troligen aldrig faktiskt breddad
till `text` då heller. Löst genom att göra breddningen SJÄLV, direkt i
SQL (`db/migrations/2026-07-25-widen-text-columns-directly.sql`) - drop
search_vector, `ALTER COLUMN ... TYPE text` på alla fyra
`columnDefinition = "text"`-kolumnerna (grapes/tasting_notes/
systembolaget_description/munskankarna_review, ofarligt no-op om redan
text), utan att förlita sig på att Hibernate lyckas med det vid nästa
uppstart. Efter det har Hibernates `ddl-auto: update` inget kvar att
göra för de här kolumnerna alls - dess förväntade typ matchar redan
verkligheten, så konflikten kan inte uppstå igen. **Lärdom:** lita inte
på att en enskild lyckad deploy bevisar att en `ddl-auto: update`-driven
ALTER faktiskt gick igenom - transaktionsbeteendet vid ett DDL-fel kan
dölja att den tysta uteblev.

**Andra deploy-fällan, samma rotorsak (2026-07-25): sökningen kraschade
i produktion efter att schemat väl gick igenom.** `WineJpaRepository.
search(...)` är en native query med en HÅRDKODAD, explicit kolumnlista
(medvetet, se klasskommentaren - `search_vector` är en genererad kolumn
som inte får plockas upp av `SELECT *`) - den listan uppdaterades aldrig
när `owner_id` (WINE-10) lades till som ett mappat fält på `WineEntity`,
så Hibernate kraschade vid hydrering: `The column name owner_id was not
found in this ResultSet`. Fixat genom att lägga till `owner_id` sist i
kolumnlistan. **Lärdom, dokumenterad direkt i `WineJpaRepository`s
Javadoc:** den här kolumnlistan måste hållas i synk med VARJE mappat
fält på `WineEntity`, inte bara de ursprungliga - lätt att missa eftersom
`WineEntity`-fält annars bara kräver en ny kolumn/getter/setter, inget
annat ställe att komma ihåg.
- **Ingen befintlig automatisk test fångade det här** - `soka-viner.
  feature` kör bara mot `InMemoryWineRepository` (Cucumber-standardmönstret
  för snabba scenarier), och `vin-persistens.feature` (den enda
  Testcontainers-baserade Postgres-svit som fanns) testade bara
  omstart, inte sökning. Täppt igen med ett nytt scenario,
  `sokning-mot-postgres.feature`, som kör `WineRepository.search(...)`
  mot en riktig Postgres via samma `PersistenceSteps`-klass (utökad,
  inte en ny - delar redan Spring-wired `WineService`/`JpaWineRepository`
  med omstartsscenariot). Verifierat: hade fångat exakt den här buggen
  om det funnits innan WINE-10 landade.

**Tredje deploy-fällan, samma symptom igen (2026-07-25) - den slutgiltiga,
strukturella lösningen.** Trots två raka manuella migreringar (droppa
search_vector; direkt bredda grapes/tasting_notes/
systembolaget_description/munskankarna_review till text) kom exakt
samma `cannot alter type of a column used by a generated column`-fel
tillbaka på nästa deploy (WINE-11, som inte ens rörde `WineEntity`).
Grundorsaken till VARFÖR Hibernates `ddl-auto: update` upprepat vill
röra de här kolumnerna kunde aldrig fastställas med säkerhet (troligen
en intern detalj i hur `AbstractSchemaMigrator` beslutar att göra en
fullständig kolumngenomgång av en tabell så fort NÅGOT annat skäl finns
att röra den, inte konsekvent kopplat till om typen faktiskt matchar) -
istället för att fortsätta jaga symptomet togs beslutet att ta bort
själva MÖJLIGHETEN att krascha.
- **Lösning: `search_vector` underhålls nu via en TRIGGER
  (`wines_update_search_vector()` + `wines_search_vector_trigger`),
  inte `GENERATED ALWAYS AS ... STORED`.** En vanlig `tsvector`-kolumn
  har ingen Postgres-begränsning mot att ALTER:a kolumner den beror på -
  grapes m.fl. kan Hibernate göra vad den vill med framöver, oavsett
  varför, utan att någonsin kunna blockeras igen. `schema.sql` skrevs om
  i sin helhet (samma DROP+återskapa-princip som förut, nu för
  funktion/trigger/kolumn istället för en genererad kolumn).
- **Ny fälla upptäckt under lokal verifiering innan produktion
  rördes igen:** Spring Boots `ScriptUtils` (kör `schema.sql` via
  `spring.sql.init.mode: always`) delar upp filen i separata JDBC-anrop
  genom enkel strängsökning efter `;` - den förstår inte PL/pgSQL:s
  `$$...$$`-citerade funktionskroppar och kapade
  `CREATE FUNCTION ... AS $$ BEGIN ... RETURN NEW; END; $$` mitt i vid
  det första `;` inuti funktionen (`Unterminated dollar quote`).
  Löst med `spring.sql.init.separator: ";;"` i `application.yml` - varje
  toppnivåsats i `schema.sql` avslutas nu med `;;` istället för `;`,
  medan de vanliga enstaka `;`-tecknen inuti funktionskroppen lämnas
  orörda och tolkas korrekt av Postgres självt (som förstår
  dollar-citering, till skillnad från Spring Boots enkla
  strängbaserade uppdelning).
- **Verifierat lokalt på det mest realistiska sättet möjligt innan
  produktionen rördes en tredje gång:** en lokal databas fick manuellt
  återskapa exakt produktionens trasiga tillstånd (grapes m.fl. som
  `varchar(255)`, `search_vector` som `GENERATED ALWAYS AS`), appen
  startades om mot det - startade rent, och `\d wines` efteråt visade
  att Hibernate den gången inte ens försökte röra `grapes` (ingen rad i
  loggen), vilket bekräftar att beteendet verkligen är opålitligt/
  svårförutsägbart snarare än en konsekvent bugg att fixa vid källan.
  Kolumnerna förblev `varchar(255)` - medvetet accepterat, inte ett
  problem i praktiken (`to_tsvector` fungerar identiskt på `varchar`
  och `text`, och 255 tecken har varit produktionens verklighet hela
  tiden fram tills det här). Sökning och registrering testade
  end-to-end via curl mot den återhämtade lokala appen innan push.
  `db/migrations/2026-07-25-search-vector-trigger-instead-of-generated.sql`
  kördes sedan mot produktionsdatabasen FÖRE deployen, av samma
  kapplöpningsskäl som de två tidigare migreringarna (Hibernate hinner
  annars krascha innan `schema.sql` får chansen att köras).
  **Lärdom om testmetodik:** de två tidigare migreringarna verifierades
  bara genom att pusha och se om produktionen råkade fungera - den
  här gången återskapades det FAKTISKA trasiga tillståndet lokalt
  först, vilket är vad som borde ha gjorts från början.

**WINE-12 byggd (2026-07-24): formulärinloggning ersätter HTTP Basic,
med en viktig avvikelse från ursprungsplanen.** `SecurityConfig` bytte
`.httpBasic(...)` mot `.formLogin(...).loginPage("/login").permitAll()`
+ `.logout(...)`. CSRF slogs på igen (var avstängt sedan ADR 0009, av
skäl som inte längre gäller när autentiseringen är sessionsbaserad):
`thymeleaf-extras-springsecurity6` (nytt beroende) injicerar automatiskt
CSRF-fältet i varje `th:action`-formulär (`login.html`,
`vin-formular.html`), och `vinkallare.html` fick en
`htmx:configRequest`-lyssnare som lägger till CSRF-headern på htmx-anrop
(`hx-delete` på "Ta bort"-knapparna) - läst från två `<meta>`-taggar i
`<head>`. Ny `LoginController` (`GET /login`) + `login.html` (enkel
formulärsida, visar felmeddelande vid `?error`, "Du är utloggad" vid
`?logout`). Utloggning är en liten `<form method="post"
th:action="@{/logout}">`-knapp bredvid "Lägg till vin".
- **Fälla i `<head>`:** `document.body.addEventListener(...)` i ett
  inline-`<script>` i `<head>` kraschar - `document.body` finns inte
  förrän `<body>` har parsats. Löst med `document.addEventListener(...)`
  istället (eventet bubblar upp till `document` ändå).
- **Avsiktlig avvikelse från WINE-12s ursprungliga story-text:**
  `UserDetailsService` är KVAR som den hårdkodade
  `InMemoryUserDetailsManager` (admin/readonly), INTE bytt till att läsa
  från den nya `UserRepository`n. Att byta redan här hade slagit ut
  admin/readonly-inloggningen direkt (ingen rad i `users`-tabellen ännu,
  eftersom WINE-11/registrering inte finns) - både lokalt och i
  PRODUKTIONEN, där admin-kontot faktiskt används för att sköta den
  riktiga vinsamlingen. Databasbytet hör hemma i WINE-11 istället.
  Konsekvens: en oautentiserad förfrågan mot en skyddad resurs svarar nu
  302 till `/login` istället för 401 (`LoginUrlAuthenticationEntryPoint`
  istället för `BasicAuthenticationEntryPoint`) - alla
  "utan inloggning"-tester i `WineControllerTest` uppdaterade därefter.
- **Testfälla, `@MockBean`-läckage:** ett första försök löste
  CSRF-i-tester generellt via en `@TestConfiguration`
  (`MockMvcBuilderCustomizer` som satte `defaultRequest(get("/").
  with(csrf()))`) - fungerade för enskilda nästlade testklasser isolerat,
  men fick `wineService`/`labelInterpretationService` (`@MockBean`) att
  INTE nollställas mellan tester när HELA `WineControllerTest`-klassen
  kördes i ett svep: stubbning/anrop från ett tidigare test läckte in i
  senare tester (`verify(..., never())` misslyckades med anrop som
  tillhörde ett helt annat test, och en `checkForDuplicate`-stubbning
  från ett test påverkade nästa). Orsaken är inte helt klarlagd, men
  bytt till att lägga `.with(csrf())` explicit på varje POST/DELETE/
  multipart-anrop istället (fler rader, men bevisat säkert - 72/72
  gröna) löste det helt. Värt att komma ihåg: undvik
  `MockMvcBuilderCustomizer`/`defaultRequest`-mönstret för `@WebMvcTest`
  i det här projektet tills orsaken är förstådd.
- **`WineControllerTest` fick en ny `InloggningOchUtloggning`-svit** som
  gör en RIKTIG inloggningsrundtur (`SecurityMockMvcRequestBuilders.
  formLogin()`, mot den faktiska `UserDetailsService`n/lösenordet, inte
  `user(...)`) - de tre WINE-12-scenarierna (rätt/fel uppgifter,
  utloggning avslutar sessionen). Övriga 40+ tester använder
  `SecurityMockMvcRequestPostProcessors.user(...)` (ren
  SecurityContext-injicering, ingen riktig autentisering) - medvetet:
  de bryr sig om `WineController`s rendering/åtkomst, inte om
  autentiseringsmekaniken, och en riktig inloggningsrundtur i var och en
  hade varit onödigt dyrt.
- **`WineListResponsiveIT`/`LabelScanFormIT` (Playwright)** bytte från
  `Browser.NewContextOptions().setHttpCredentials(...)` till en riktig
  formulärinloggning (öppna `/login`, fylla i fälten, klicka, stänga
  sidan) innan varje test - sessionscookien sätts på `BrowserContext`-
  nivå av Playwright och följer med alla senare sidor i samma kontext.
  Verifierat: samtliga 45 IT-tester (inklusive de tre etikettskannings-
  testerna, som initialt fastnade i en 30s timeout eftersom
  `/wines/nytt` bara omdirigerade till den nu obefintliga
  Basic-auth-inloggningen) gröna via `mvn verify`.

**WINE-11 byggd (2026-07-25): öppen självregistrering.** Ny
`GET/POST /registrera` (`RegistrationController` + `registrera.html`),
`RegistrationService` (`application/`, sparar via den redan byggda
`UserRepository`n från WINE-10) och `RegistrationResult`
(`Registered`/`UsernameTaken`, samma sealed interface-mönster som
`DuplicateCheck`/`LabelInterpretationResult`). Länk till/från
`login.html`.
- **`SecurityConfig.userDetailsService` slår nu ihop två källor** - de
  gamla hårdkodade `admin`/`readonly`-kontona (kollas först) och
  `UserRepository`n (kollas bara om användarnamnet inte matchar någon av
  de hårdkodade, via `UsernameNotFoundException`-fallback). Detta var
  den medvetna avvikelsen från WINE-12 (se ovan) som nu löses här -
  admin/readonly fortsätter fungera oförändrat, samtidigt som
  nyregistrerade användare kan logga in. Databasen är fortfarande INTE
  den enda sanningskällan - det blir den först i WINE-15, när
  admin/readonly-kontona tas bort.
- **Nyregistrerade användare får `ROLE_ADMIN` hårdkodat**
  (`RegistrationController.loggaInAutomatiskt`), en medveten temporär
  förenkling - `SecurityConfig`s route-regler kräver fortfarande den
  rollen för det mesta, och riktig scoping till den egna listan finns
  inte förrän WINE-13. Praktisk konsekvens just nu: alla inloggade
  användare (admin, readonly delvis, och alla nyregistrerade) delar i
  praktiken samma enda vinlista - "min vinlista är tom direkt efter
  registrering" (ett av WINE-11s ursprungliga Gherkin-scenarier) stämmer
  alltså INTE ännu i verkligheten, och testades medvetet inte som om det
  gjorde det. Försvinner när WINE-13 landar.
- **Auto-inloggning direkt efter registrering** byggs manuellt
  (`UsernamePasswordAuthenticationToken`s 3-argumentskonstruktor +
  `HttpSessionSecurityContextRepository.saveContext(...)`) - samma
  mekanism `SecurityContextHolderFilter` annars sköter automatiskt vid
  en vanlig formLogin-rundtur, men här behövs den manuellt eftersom
  registreringen inte går via `/login`.
- **Testuppdelning:** `registrera-konto.feature` (Cucumber, ny
  `RegistrationSteps`-klass, samma Spring+Testcontainers-mönster som
  `PersistenceSteps`) täcker bara `RegistrationService`s kärnlogik
  (kontoskapande, unikhetskontroll) - sessionen/auto-inloggningen är ett
  webblagerskoncept och testas istället i det nya
  `RegistrationControllerTest` (`@WebMvcTest`, verifierar att
  `SPRING_SECURITY_CONTEXT`-attributet faktiskt sätts i sessionen efter
  en lyckad POST). "Jag är inloggad"/"min vinlista är tom" ur de
  ursprungliga YouTrack-scenarierna splittades alltså medvetet över två
  testlager istället för att tvinga fram ett enda HTTP-baserat
  Cucumber-scenario.
- **Fälla:** `WineControllerTest` slutade kunna bygga sin
  `ApplicationContext` (alla test i klassen fick fel, inte bara ett)
  efter `userDetailsService`-beanens nya `UserRepository`-beroende -
  fixat med ett nytt `@MockBean UserRepository` i den klassen. Värt att
  komma ihåg: varje ny bean-parameter på en `@Bean`-metod i
  `SecurityConfig` måste speglas i ALLA `@WebMvcTest`-klasser som
  `@Import(SecurityConfig.class)`, inte bara den man råkar ändra i
  stunden.
- Verifierat: `mvn verify` grön (77 enhetstester, 48 acceptans-/UI-tester).

**WINE-13 byggd (2026-07-25): vinlistan scopeas per användare.** Den mest
genomgripande storyn i Fas 1. `Wine` fick ett nytt fält, `owner`
(`User.UserId`, nullable), som en vanlig record-komponent - inte ett
separat parameter som måste skickas med vid varje anrop. Det betyder att
en redigering (`existing.toBuilder()...`) automatiskt bär vidare rätt
ägare utan att `WineController` behöver göra något extra, medan en
NY vinpost får ägaren explicit stämplad
(`.owner(currentOwner(authentication))`) i `addWine`. `WineService.
save(Wine)` fick medvetet INGET owner-argument - all ägarlogik sitter i
anropande kod (`WineController`), inte i tjänsten.
- **Alla läsande metoder scopeas, med `null` som "oscopeat" (inte "ägs av
  ingen")**: `WineRepository.findAllByOwner/findByIdAndOwner/
  searchByOwner`, motsvarande `WineService`-metoder, och båda adaptrarna
  (`JpaWineRepository`/`WineJpaRepository` med härledda
  `findByOwnerId`/`findByIdAndOwnerId`-frågor plus `:ownerId IS NULL OR
  owner_id = :ownerId` i native-sökqueryn; `InMemoryWineRepository` med
  samma null-betyder-oscopeat-logik i Java). `deleteById` scopeas INTE
  på repository-nivå - `WineService.removeWine` verifierar ägarskap via
  `findByIdAndOwner` FÖRST och anropar bara `deleteById` om det matchar
  (no-op annars, inte ett fel - samma "bete sig som att vinet inte
  fanns"-princip som gäller överallt).
- **Beslut, avstämt med användaren innan kodning: admin/readonly förblir
  HELT oscopeade (ser alla viner, som innan WINE-13) fram till WINE-15.**
  `WineController.currentOwner(authentication)` slår upp `Authentication.
  getName()` i `UserRepository` - hittas inget (de hårdkodade
  admin/readonly-kontona finns inte i `users`-tabellen) blir owner
  `null`, vilket ovanstående null-betyder-oscopeat-logik tolkar som "visa
  allt". Bara riktigt registrerade konton (WINE-11) får en verklig,
  privat lista. Motiverat av produktionsrisk - att kräva ett `UserId`
  för admin hade antingen låst ute den riktiga produktionsanvändaren
  (tom lista tills WINE-17s migrering körts) eller krävt att en
  `users`-rad skapades åt admin i förväg, bägge större ingrepp än
  motiverat just nu.
- **`editForm`/`saveEdit`/`showImage`/`increaseQuantityForDuplicate` fick
  alla ett nytt `Authentication`-parameter** (saknades helt innan) för
  att kunna scopea. Samtidigt byttes `orElseThrow(() -> new
  IllegalArgumentException(...))` mot `orElseThrow(() -> new
  ResponseStatusException(HttpStatus.NOT_FOUND))` i `editForm`/
  `saveEdit` - fixar en redan existerande, oberoende bugg (ett okänt
  vin-id gav 500, inte 404) som blev akut relevant nu eftersom en
  ägarmiss ska bete sig identiskt med "vinet finns inte" (WINE-14).
  `showImage` hade redan rätt mönster (`ResponseStatusException` för
  saknad bild) sedan tidigare.
- **Två separata testfällor hittade under verifiering, ingen av dem i
  produktion tack vare lokal end-to-end-testning innan push:**
  1. `WineControllerTest`s globala sök-/ersätt-fix
     (`findById(new WineId(1L))` → `findById(new WineId(1L), any())`)
     blandade en rå parameter med en Mockito-matcher i samma anrop -
     `InvalidUseOfMatchersException` ("2 matchers expected, 1
     recorded"). Mockito kräver att ANTINGEN alla argument är matchare
     ELLER inga är det. Fixat med `eq(new WineId(1L))` istället för det
     råa värdet.
  2. **Allvarligare, hade nästan missats:** `WineEntity.owner` var
     `@ManyToOne(fetch = FetchType.LAZY)` (satt i WINE-10). `open-in-
     view: false` stänger Hibernate-sessionen så fort ett
     repository-anrop returnerar, och `JpaWineRepository.toDomain(...)`
     läser `entity.getOwner().getId()` EFTER det - `org.hibernate.
     LazyInitializationException: could not initialize proxy ... no
     Session`, kraschade varenda sida som listade viner. Upptäcktes
     INTE av `mvn verify` (Testcontainers-scenarierna sparar bara ett
     vin i taget utan att läsa tillbaka via en lista i samma
     session-brytning som exponerade buggen) - bara av en manuell,
     verklig flerkonto-rundtur lokalt (registrera två användare, lägg
     till var sitt vin, hämta listan via en separat `curl`-förfrågan).
     Fixat genom att byta till `FetchType.EAGER` - `UserEntity` är litet
     (fyra fält) och samlingen för liten för att en extra join per vin
     ska vara en verklig kostnad. **Lärdom:** `mvn verify`s
     Testcontainers-scenarier bevisar att en enskild sparning/hämtning
     fungerar, inte att en hel lista av flera repository-anrop i
     följd (som en riktig sidladdning gör) fungerar utanför en enda
     transaktion - bara en riktig HTTP-rundtur mot en riktig,
     körande app avslöjade det här.
- **Verifierat manuellt, end-to-end, mot en riktig lokal Postgres innan
  push:** två konton (alice, bob) registrerade, ett vin vardera,
  bekräftat att var och en bara ser sitt eget i listan, att den andras
  vin ger 404 på både redigeringssida och bildlänk, att ett
  borttagningsförsök mot någon annans vin är ofarligt (200, men vinet
  finns kvar), och att admin (inloggad separat) ser båda vinerna.
  `mvn verify` grön (77 enhetstester, 48 acceptans-/UI-tester).

**WINE-18 (2026-07-25): uppfylld utan kod.** WINE-13:s null-betyder-
oscopeat-design gjorde att alla befintliga Cucumber-stegklasser förblev
opåverkade (de skickar redan `null` till de nya owner-parametrarna) -
inget behövde ändras för att hålla dem gröna. Markerad klar direkt, se
YouTrack-kommentaren.

**WINE-14 byggd (2026-07-25): dataisolering, ett eget automatiskt test
för det som redan verifierats manuellt under WINE-13.** Splittat över
två testlager, medvetet - samma princip som resten av projektet (se
README:s Arbetsprocess):
- **Listans osynlighet** (`flera-anvandare.feature`, ny `MultiUserSteps`
  - egen stegklass, inte återanvänd `WineService`-instans från någon
  annan stegklass, se "Kända fällor" om varför delade Gherkin-steg måste
  ligga i samma klass). Första stegklassen som modellerar FLERA
  inloggade användare samtidigt - en `Map<String, UserId>` håller reda
  på vilket konto varje Gherkin-användarnamn ("alice", "bob") faktiskt
  fick, skapat lat första gången namnet nämns i scenariot, via en egen
  `InMemoryUserRepository`.
- **Direkt URL-åtkomst** (ny `DataiseleringMellanAnvändare`-svit i
  `WineControllerTest`, webblagret) - "kan inte komma åt ett annat vin
  via URL" är i grunden ett HTTP-koncept (statuskod), inte något
  applikationslagret har ett naturligt sätt att uttrycka. `WineService`
  är redan stubbad i den testklassen, så det som simuleras är "findById
  returnerar tomt" (exakt vad WineService faktiskt gör för ett vin som
  ägs av någon annan) - inte en riktig andra användare på HTTP-nivå.
  Täcker redigeringssida (GET), spara redigering (POST) och bildvisning
  (GET), alla → 404.
- Ingen ny produktionskod - bara tester, byggda ovanpå det som WINE-13
  redan implementerade och som redan verifierats manuellt (två riktiga
  konton mot en riktig Postgres, se WINE-13-loggen ovan). Verifierat:
  `mvn verify` grön (80 enhetstester, 49 acceptans-/UI-tester).

**WINE-17 körd (2026-07-25): befintliga produktionsviner (~30 st) knutna
till kontot "Testus".** `db/migrations/2026-07-25-assign-existing-wines-
to-testus.sql` - en `DO $$ ... $$`-sats som slår upp `Testus`s `UserId`
och sätter `owner_id` på alla rader där det fortfarande var `NULL`,
med en explicit `RAISE EXCEPTION` om användarnamnet inte skulle hittas
(hellre ett högljutt fel än att tyst göra ingenting). Bekräftat i
produktionen: Testus ser nu alla tidigare ägarlösa viner.
- **Backupförsöket innan migreringen misslyckades, medvetet övergivet
  av användaren.** `pg_dump` gav `permission denied for table
  pg_database` mot `postgres:16`-avbildens klient (en collation-
  versionskontroll Clever Clouds databasanvändare inte har rättighet
  till), och ett försök med en äldre klient (`postgres:14`) gav istället
  `server version mismatch` (produktionsservern kör Postgres 15.4).
  `psql` (använt för själva migreringen) drabbas INTE av samma problem -
  bara `pg_dump`s extra katalogfrågor. Löst genom att användaren
  medvetet valde att köra migreringen utan backup.
- **Deltillägget "gör owner_id NOT NULL" flyttat till WINE-15, inte
  gjort här.** Kan inte göras förrän admin/readonly är borttagna - så
  länge admin kan lägga till viner (medvetet oscopeat, `owner=null`,
  se WINE-13) skulle en NOT NULL-begränsning blockera det, och admin
  var fortfarande aktivt i produktion vid det här laget.

**WINE-15 byggd (2026-07-25): ADMIN/READONLY och de hårdkodade kontona
borttagna - sista storyn i Fas 1.** `SecurityConfig` skriven om i
grunden: `UserDetailsService` läser numera bara från `UserRepository`
(databasen), `authorizeHttpRequests` är bara `.requestMatchers(
"/registrera").permitAll()` + `.anyRequest().authenticated()` - ingen
`hasRole`/`hasAnyRole` kvar någonstans. `WINECELLAR_ADMIN_PASSWORD`
borttagen ur `application.yml`. `WineController.hasAdminRole(...)` och
modellattributet `canEdit` borttagna helt (var bara ett UI-lager för
att dölja adminfunktioner för READONLY, se ADR 0009) -
`vinkallare.html`s `th:if="${canEdit}"`-vakter runt "Lägg till vin" och
`.detalj-atgarder` togs bort i samma veva, eftersom alla inloggade
användare nu har samma rättigheter till sin egen data.
[ADR 0009](docs/adr/0009-whole-app-http-basic-auth.md) markerad
Superseded av [ADR 0013](docs/adr/0013-multi-user-accounts.md), enligt
den senares egen instruktion om att göra det först när WINE-15 landar.

- **`owner_id` gjord `NOT NULL` i `schema.sql`** (`ALTER TABLE wines
  ALTER COLUMN owner_id SET NOT NULL`), inte via
  `@JoinColumn(nullable = false)` i `WineEntity` - samma
  "Hibernates `ddl-auto: update` är opålitligt för ALTER av befintliga
  kolumner"-lärdom som redan dokumenterats för `search_vector`-sagan
  ovan, fast i motsatt riktning (skärpa en begränsning istället för att
  lätta på en). Satsen är idempotent (`SET NOT NULL` på en redan
  `NOT NULL`-kolumn är ett ofarligt no-op) och förutsätter att WINE-17s
  migrering redan körts - annars skulle den misslyckas mot kvarvarande
  `NULL`-rader.
- **Städpassning av gamla Javadoc-kommentarer som fortfarande nämnde
  admin/readonly i presens** (`WineController.currentOwner(...)`,
  `AnthropicLabelInterpreter`, `WineRepository`, `WineJpaRepository`,
  `Wine.owner`) - alla omskrivna till dåtid, ingen kodändring. Värt att
  komma ihåg för framtida liknande borttagningar: en `grep` efter det
  borttagna begreppets namn hittar även "det här fanns till FÖR"-typen
  av kommentarer som blir vilseledande i presens även om de aldrig var
  fel när de skrevs.
- **Testsviten krävde en större omskrivning eftersom den bara kunde
  logga in som de nu borttagna kontona.** `WineControllerTest`s hela
  `ReadonlyKontot`-testklass (7 test) och alla `skaNekasFörReadonlyKontot`-
  enskilda tester togs bort - rolldistinktionen finns inte längre att
  testa. `InloggningOchUtloggning` bytte från riktig `formLogin()`-inloggning
  mot det hårdkodade `admin`-kontot till en `@BeforeEach` som stubbar
  `userRepository.findByUsername(...)` med ett `PasswordEncoder`-hashat
  testkonto - motiverat av att `@WebMvcTest` redan mockar bort hela
  persistenslagret, så ett riktigt registrerat konto vore fel abstraktionsnivå
  där. `WineListResponsiveIT`/`LabelScanFormIT` (båda riktiga
  `@SpringBootTest`+Testcontainers, alltså en riktig databas) fick istället
  registrera ett riktigt konto via `RegistrationService` i `@BeforeEach` och
  logga in som det - motsatt val av samma skäl, omvänt.
- **Ny testfälla, hittad av `mvn verify` (inte manuellt): `WineListResponsiveIT`
  hade tidigare TVÅ separata `@BeforeEach`-metoder (en för kontot, en för att
  lägga till ett testvin) - slogs ihop till EN, eftersom JUnit 5 inte
  garanterar körordning mellan flera `@BeforeEach` på samma klass, och
  vinets `.owner(testkontoId)` kräver att kontot redan är skapat.**
- **Den allvarligaste fällan: en klassöverskridande `@Before`-hook-krock i
  Cucumber, som bara `mvn verify` (inte kompilering, inte manuell
  verifiering) avslöjade - i tre separata omgångar.** `PersistenceSteps`
  (kör mot en riktig Postgres via Testcontainers, se ovan i det här
  avsnittet) sparar viner via `wineService.save(...)`, vilket nu kräver en
  riktig ägare eftersom `owner_id` är `NOT NULL`. Men `RegistrationSteps`
  har en egen `@Before`-hook som gör `userRepository.deleteAll()` - och
  Cucumber-JVM kör **alla** `@Before`-hooks från **alla** laddade
  stegklasser för **varje** scenario, inte bara från klasser vars steg
  faktiskt förekommer i scenariot (en ny variant av fällan CLAUDE.md redan
  varnar för under "Kända fällor", fast mellan klasser istället för inom
  en). Tre omgångar krävdes för att hitta rätt ordning:
  1. Första försöket: `PersistenceSteps` fick registrera ett testkonto
     direkt i sin befintliga `@Before`, utan explicit ordning mot
     `RegistrationSteps`. Kraschade med en FK-överträdelse mot `users`
     ("Key (owner_id)=(40) is not present in table users") - `Registration
     Steps.reset()` råkade köras EFTER `PersistenceSteps` inom samma
     scenario och tog bort kontot precis efter att det pekats ut.
  2. Andra försöket: gav `PersistenceSteps` `@Before(order = 1)` och
     `RegistrationSteps` `@Before(order = 0)`, för att tvinga users-
     raderingen att ske FÖRE kontoregistreringen. Kraschade istället med
     en ANNAN FK-överträdelse, på `users` från `wines`-sidan ("update or
     delete on table users violates foreign key constraint ... still
     referenced from table wines") - `RegistrationSteps.reset()`
     (`order = 0`) körde nu FÖRE `PersistenceSteps` hann radera
     FÖREGÅENDE scenarios viner (som låg i samma metod, `order = 1`),
     så users-raderingen stötte på kvarvarande viner som fortfarande
     pekade på de kontona.
  3. **Lösningen:** dela upp `PersistenceSteps`s enda `@Before`-metod i
     TVÅ - `raderaAllaViner()` (`order = -1`, radera viner FÖRST) och
     `registreraTestkonto()` (`order = 1`, registrera kontot SIST) - med
     `RegistrationSteps.reset()` (`order = 0`, radera users) i mitten.
     Den tredelade ordningen (viner → users → nytt testkonto) är den enda
     som är FK-säker i båda riktningarna: viner måste bort innan deras
     ägare får raderas, och det nya testkontot måste skapas efter att
     users-tabellen redan är tömd. **Lärdom:** när två stegklassers
     globala `@Before`-hooks rör samma tabeller i motsatta riktningar
     (en skapar det den andra raderar), räcker det inte att bara
     tvinga EN inbördes ordning mellan de två metoderna - en delad
     resurs som både måste tömmas OCH fyllas på i rätt ordning kan kräva
     att en av metoderna delas upp så att delarna interfolieras med den
     andra klassens hook.
- **Verifierat lokalt end-to-end mot en riktig, färsk lokal Postgres
  (docker-compose, inte bara Testcontainers) innan push:** `admin`/`admin`
  och `readonly`/`readonly` ger båda `/login?error` (kontona finns inte
  längre), ett nytt konto kan registreras och loggas in automatiskt,
  ett vin kan läggas till/redigeras/tas bort/sökas fram, en uppladdad
  bild hämtas tillbaka byte-identisk med korrekt `Content-Type`, och ett
  andra registrerat konto varken ser det första kontots vin i listan
  eller kommer åt det via direkt URL (404). `mvn verify` grön i sin
  helhet efter hook-ordningsfixen ovan (samtliga enhetstester,
  acceptanstester och Playwright-IT-tester, inklusive
  `WineListResponsiveIT`/`LabelScanFormIT`s omskrivna inloggning).

Detta avslutar Fas 1 (WINE-9 till WINE-18) - appen stödjer nu flera
oberoende användare, var och en med sin egen, helt privata vinsamling.

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
Bild-kolumnen i själva Excel-filen (Excels "bild i cell", inbäddad rich
data) importeras fortfarande medvetet inte - se README:s "Import och
export av Excel-data" för kommandon och `VinradParser`/`ImportExcel` för
implementationen.

**Etikettimport från en bildmapp (byggt 2026-07-19, miljövariabeln döpt
om till `WINECELLAR_LOCAL_IMAGE_FOLDER` 2026-07-22 när ExportExcel
började skriva till samma mapp - se nedan).** Separat väg in för
bilder: `Bildmatchare` (ny klass i samma modul) matchar filer i en mapp
(valfri miljövariabel - miljövariabel
istället för ett nytt positionellt argument av samma PowerShell-
citattecken-skäl som `jdbc-url`/`användare`/`lösenord`) mot varje vins
`name`-fält, exakt filnamnsmatchning (stam utan ändelse, känner igen
jpg/jpeg/png/gif/webp). `ImportExcel.main` kopplar bilden på varje
`Wine` via `withImage(...)` **innan** insert, så `INSERT_SQL` fick två
nya kolumner (`image`, `image_mime_type`) - enklare än att göra en andra
databasrunda som matchar tillbaka mot redan infogade rader (som hade
krävt `RETURN_GENERATED_KEYS` eller en efterföljande `UPDATE...WHERE
name=`). Två tvetydighetsfall hanteras explicit med utskrivna varningar
istället för att gissa, eftersom verktyget skriver direkt mot
produktionsdata utan granskningssteg: samma filnamnsstam med flera
ändelser (hoppas över) och flera viner med exakt samma namn i
Excel-filen (samma bild kopplas till alla, varning skrivs ut så det
syns). `BildmatchareTest` täcker matchning, MIME-typer per ändelse,
okänd filändelse och båda tvetydighetsfallen.

**Systembolagets produktnummer fick en egen Excel-kolumn (2026-07-20).**
Källfilen hade tidigare produktnumret hopklistrat som första raden i
samma cell som beskrivningen (`"12345\nBeskrivning..."`), delat på den
första radbrytningen i `VinradParser` (`systembolagetProduktnummer`/
`systembolagetBeskrivning`). Användaren lade till en ny kolumn
"Systembolagets prodnummer" direkt efter "Eget betyg" i sin egen
Excel-fil - `COL_SYSTEMBOLAGET_PRODUKTNUMMER = 15` sköt in sig där,
vilket flyttade `COL_SYSTEMBOLAGET` (nu bara beskrivningen, ingen
radbrytning kvar) och alla kolumner efter den ett steg åt höger
(kolumnlayouten är nu A-V, inte A-U). De två hopklistrings-metoderna i
`VinradParser` är borttagna - båda fälten läses nu direkt med den
vanliga `text(row, col)`-hjälparen, som två oberoende kolumner.
`VinradParserTest` uppdaterad med de nya kolumnindexen.

**Exportskript tillagt, samma modul (byggt och verifierat 2026-07-22).**
`ExportExcel` läser `wines`-tabellen och skriver en `.xlsx` i exakt samma
kolumnlayout som `VinradParser` förväntar sig - se README:s "Import och
export av Excel-data" för kommandot. Två nya klasser:
- `VinradSkrivare` gör radskrivningen, en `Wine` → en POI-`Row`. Delar
  `VinradParser`s `COL_*`-konstanter (som därför gick från `private` till
  paketsynliga) istället för att duplicera kolumnindexen i en andra
  klass - att hålla två separata index-uppsättningar i synk manuellt är
  precis det felmönster som redan hänt en gång i det här projektet
  (Systembolagets-prodnummer-kolumnen ovan). `SVENSK_VINTYP` är en egen
  lokal `Map<WineType,String>` i `VinradSkrivare` (den omvända mappningen
  av `VinradParser`s `VINTYPER`) - `WineType` har fortfarande medvetet
  ingen `.label()`-metod, se `WineType`-noteringen i Domänmodell-avsnittet;
  varje anropsplats som behöver den svenska texten duplicerar sin egen
  lilla karta, och det gäller nu även skrivriktningen.
- `Databaskoppling` extraherades ur `ImportExcel` (samma
  `jdbcUrlFrånMiljö`/`miljövariabelEllerStandard`-logik, ordagrant
  flyttad) eftersom `ExportExcel` behövde exakt samma
  anslutningsuppslagning - först vid den andra verkliga anropsplatsen,
  inte i förväg.
- `Wine.vintage`/`Wine.quantity` är `Integer` (inte primitiv `int`) sedan
  "bara namnet obligatoriskt"-ändringen ovan - `ExportExcel` läser dem med
  `ResultSet.getObject(kolumn, Integer.class)` (ger korrekt `null`, till
  skillnad från `getInt` som ger `0` för både `0` och `NULL`), och
  `VinradSkrivare.heltal(...)` tar `Integer` och lämnar cellen helt oskapad
  vid `null` - inte en tom cell, ingen cell alls (samma mönster som
  `text(...)`/`decimal(...)` redan använde).
- **Rundtursbegränsningen (VinradParser krävde vintyp/land/producent/namn
  vid återimport, trots att webb-UI:t bara kräver namn) löstes samma dag,
  på användarens uttryckliga begäran om en fullständig rundtripp** - se
  separat avsnitt nedan. `VinradSkrivareTest`s tidigare
  `ettVinMedBaraNamnetSkrivsMenHoppasÖverVidÅterimport` (som dokumenterade
  begränsningen som ett test) döptes om till
  `ettVinMedBaraNamnetSkrivsOchÅterlässKorrekt` och asserterar nu att
  raden läses tillbaka korrekt istället för att kasta ett undantag.
- **Fälla som dök upp vid den manuella verifieringen:** `pom.xml`s
  `exec-maven-plugin` hade `<mainClass>` hårdkodat direkt till
  `ImportExcel` (inte via en `${...}`-property) - `-Dexec.mainClass=...
  ExportExcel` på kommandoraden gjorde alltså ingenting, `mvn exec:java`
  körde tyst `ImportExcel` istället. Fixat med en `exec.mainClass`-
  property (standard `ImportExcel`, så den vanliga importkörningen är
  opåverkad) och `<mainClass>${exec.mainClass}</mainClass>` i
  pluginkonfigurationen.
- Verifierat lokalt mot en tom docker-compose-databas: två testviner
  (ett fullständigt, ett namn-bara) skapades via webb-UI:t, exporterades,
  databasen tömdes (`TRUNCATE`), filen importerades tillbaka - det
  fullständiga vinet återkom med identiska värden i alla fält
  (kontrollerat via `psql`), det namn-bara vinet hoppades över med
  varningen precis som förväntat. `VinradSkrivareTest` (4 tester) täcker
  radskrivningen isolerat med en skriv-och-återläs-rundtur mot
  `VinradParser`, samma testfilosofi som modulens övriga tester (ingen
  Gherkin här, se README:s Arbetsprocess-avsnitt för varför den
  distinktionen finns).

**Bildexport tillagd (byggt 2026-07-22, samma dag - på användarens
uppföljningsfråga "Går det att även exportera bilderna?"), sedan utökad
till en fullständig rundtripp samma dag (på uttrycklig begäran - "Ja,
jag vill ha fullständig rundtripp").**

Första omgången: `VinradSkrivare.bild(...)` ankrar varje vins `image`
som en vanlig POI-`Picture` i `VinradParser.COL_BILD` (kolumn I, gick
från en ren kommentar till en riktig delad konstant för tillfället, se
`VinradParser`). **Detta är en helt annan mekanism än källfilens
ursprungliga "bild i cell"** (inbäddad rich data, se klasskommentaren
högst upp i det här avsnittet) - en vanlig ankrad `Picture` är mycket
enklare att SKRIVA än rich-data-celler är att LÄSA, men `ImportExcel`
läser fortfarande inte den ankrade bilden tillbaka från xlsx-filen.
- **MIME-typstöd:** JPEG/PNG/GIF (`POI_BILDTYP_PER_MIME` i
  `VinradSkrivare`, mappar till `Workbook.PICTURE_TYPE_JPEG`/`_PNG` och
  `XSSFWorkbook.PICTURE_TYPE_GIF` - GIF-konstanten finns bara på
  `XSSFWorkbook`, inte basgränssnittet `Workbook`). **Inte WEBP** - trots
  att `Bildmatchare` känner igen `.webp`-filer vid import finns inget
  OOXML-bildformat för webp och ingen motsvarande POI-konstant. En
  webp-bild hoppas över vid xlsx-inbäddning med en utskriven varning
  istället för att krascha - samma "hantera explicit med varning istället
  för att gissa/krascha"-linje som `Bildmatchare`s egna tvetydighetsfall.
- **API-detaljer:** `Drawing<?>` (sidans "canvas" för ankrade figurer)
  skapas en gång per sheet (`sheet.createDrawingPatriarch()`) och delas
  mellan alla rader, av samma återanvändningsskäl som `CellStyle
  datumformat` redan delas - `ExportExcel.main` skapar båda en gång och
  skickar in dem till `VinradSkrivare.skriv(...)`. Ankaret sätts med
  `Drawing.createAnchor(0,0,0,0, COL_BILD, rad, COL_BILD+1, rad+1)` (en
  cellstorlek, ingen anpassad radhöjd/kolumnbredd - ren
  databackup-prioritet, inte visuell polish).

**Andra omgången (samma dag): full rundtripp, tre samverkande ändringar.**
Xlsx-inbäddningen ovan blev kvar oförändrad (fortfarande bara en visuell
bekvämlighet), men den faktiska rundtrippen löstes med tre samtidiga
ändringar:
1. `VinradParser` lättades till samma regel som webb-UI:t: bara namnet
   är obligatoriskt, se "Bara namnet obligatoriskt" i Excel-import-
   avsnittet ovan.
2. `Bildmatchare.ÄNDELSE_PER_MIME` (ny, paketsynlig karta, omvänd
   riktning av den befintliga `MIME_PER_ÄNDELSE` - jpg valt som kanonisk
   ändelse för image/jpeg, inte jpeg) lades till för att `ExportExcel`
   ska kunna räkna ut vilken filändelse en bild ska få.
3. `ExportExcel.skrivBildfiler(...)` skriver varje vins bild som en
   riktig fil i `WINECELLAR_LOCAL_IMAGE_FOLDER` (**samma miljövariabel
   som import redan använde, döpt om från `WINECELLAR_IMPORT_IMAGE_FOLDER`
   - namnet ska spegla att mappen nu delas åt båda hållen**), döpt exakt
   som vinets namn. Det är DEN HÄR filen (inte den ankrade xlsx-bilden)
   som `Bildmatchare` läser tillbaka vid en efterföljande `ImportExcel`-
   körning - därför måste `WINECELLAR_LOCAL_IMAGE_FOLDER` pekas ut vid
   BÅDE export och återimport för att bilder ska följa med. Till
   skillnad från xlsx-inbäddningen har filskrivningen inget
   formatstöd-hål: alla MIME-typer `Bildmatchare` känner igen (inklusive
   webp) skrivs hit. Samma varningsmönster som `ImportExcel`s egen
   `varnaOmDubblettnamnMedBild` tillämpas i omvänd riktning: om flera
   viner delar exakt samma namn skrivs en varning ut (bara den sist
   skrivna filen blir kvar i mappen) - en ny, separat metod i
   `ExportExcel`, inte en delad abstraktion med importsidans variant
   (olika meddelandetext, för liten kod för att vara värt att slå ihop).

**Fälla som dök upp under den manuella rundtrippsverifieringen (inte av
något automatiskt test):** `ImportExcel.bindParametrar` band tidigare
`wine_type`/`producer`/`country` direkt (`statement.setString(i++,
vin.wineType().name())` osv.) utan null-koll, eftersom `VinradParser`
tidigare GARANTERADE att de aldrig var `null`. Så fort `VinradParser`
lättades (ändring 1 ovan) kraschade en återimport av ett namn-bara vin
med `NullPointerException: Cannot invoke "WineType.name()" ... is null`
istället för att spara `null` i databasen. Fixat genom att binda alla
tre null-safe via `settNullbarSträng(...)`, samma mönster som redan
användes för `region`/`subregion`/`grapes` m.fl. **Lärdom:** en ändring
i en delad parser/valideringsregel måste spåras till ALLA anropsplatser
som förlitat sig på den gamla garantin, inte bara den plats ändringen
gjordes - precis den sortens bugg som bara en verklig databasrundtur
(inte en isolerad enhetstest av en enskild klass) avslöjar.

Verifierat lokalt: tre testviner (ett fullständigt med bild, ett
namn-bara utan bild, ett namn-bara **med** bild - det sista fallet är
den viktigaste nya kombinationen) sparades via webb-UI:t, exporterades
med `WINECELLAR_LOCAL_IMAGE_FOLDER` satt, databasen tömdes (`TRUNCATE`),
och återimporterades från både xlsx-filen och bildmappen - alla tre
viner återkom med identiska textvärden, båda bilderna (inklusive det
namn-bara vinets) återkom byte-för-byte identiska med originalen, och
ingen rad hoppades över. `VinradSkrivareTest` fick motsvarande
enhetstester (byte-identiska PNG-inbäddning i xlsx via
`Workbook.getAllPictures()`, webp hoppas över vid xlsx-inbäddning utan
att krascha, ett namn-bara vin skrivs och återläses korrekt) - men
`ExportExcel.skrivBildfiler` självt har ingen egen enhetstest, verifierad
manuellt enligt samma princip som modulens övriga JDBC-integration.

**Körd mot produktionsdatabasen (2026-07-17), 30 viner sparade utan fel.**
Klever Cloud har inget CLI/konsol att köra verktyget *på* - det behövs
inte heller, det körs lokalt och pratar med Postgres-tillägget över
nätverket (nåbart utifrån, samma anslutning som t.ex. DBeaver/psql skulle
använda), pekat mot produktionens `POSTGRESQL_ADDON_*`-uppgifter från
Clever Cloud-konsolen istället för `localhost`. Ingen dedupliceringslogik
i verktyget - kör inte en gång till mot samma databas, det skulle skapa
dubbletter.

## Fas 2 (import/export via webben) - påbörjad 2026-07-25

**Beslutet är skrivet ner som [ADR 0014](docs/adr/0014-web-based-excel-import-export.md)
innan implementationen, samma mönster som Fas 1:s [ADR 0013](docs/adr/0013-multi-user-accounts.md)**
- avstämt med användaren i en diskussion (retirera CLI-verktyget helt,
återanvänd WINE-6:s dubblettidentitet, torrkörning/förhandsgranskning
före commit, EN gemensam dubblettstrategi per import, bildnamngivning
`<producent>_<namn>_<årgång>` med fallback, export som xlsx+zip).
Stories WINE-19 (ADR) till WINE-26 i YouTrack, länkade med "depends on"
i den tänkta byggordningen: WINE-19 → WINE-20 (flytta parser/writer in
i huvudappen, ta bort CLI-modulen) → WINE-21 (bildnamngivning) →
WINE-22 (webbexport xlsx)/WINE-24 (webbimport torrkörning) →
WINE-23 (bildexport zip)/WINE-25 (webbimport commit) → WINE-26
(acceptanstest/Playwright).

**WINE-20 byggd (2026-07-25): `tools/import-excel/` (hela modulen,
inklusive `pom.xml`, `ImportExcel`, `ExportExcel`, `DatabaseConnection`)
är borttagen.** `WineRowParser`, `WineRowWriter`, `ImageMatcher` (och
deras enhetstester) flyttades **oförändrade i beteende** till
`infrastructure/excel/` i huvudappen - bara paketnamnet ändrades, plus
att klasserna (och de metoder/konstruktörer/nästlade typer som andra
lager behöver anropa: `parse`, `write`, `ImageMatcher`-konstruktorn,
`findImage`, samt de nästlade `RowMissingRequiredFieldsException`/
`Image`) gick från paketprivata till `public` - annars vore flytten
meningslös, koden hade fortfarande bara varit anropbar inom sitt eget
paket. De paketprivata `COL_*`-konstanterna (`WineRowParser`) och
`EXTENSION_BY_MIME` (`ImageMatcher`) lämnades paketprivata - `WineRowWriter`
ligger kvar i SAMMA paket (`infrastructure.excel`), så deras delade
källa-till-sanning-mönster är opåverkat.
- **`ImageMatcher`s matchning är fortfarande namn-bara** i den här
  storyn - bytet till `<producent>_<namn>_<årgång>` med fallback är
  medvetet WINE-21:s jobb, inte en del av flytten (ren refaktorering,
  ingen beteendeändring).
- **`DatabaseConnection`, `ImportExcel`, `ExportExcel` flyttades INTE**
  - de var CLI-specifika (rå JDBC förbi `WineService`, kommandoradsargument,
  `System.out`-varningar) och ersätts av riktiga webbcontrollerroutes i
  WINE-22 till WINE-25, som skriver via `WineService.save(...)` istället
  för direkt SQL (ägarskopning + dubblettkontroll "gratis").
- **Apache POI (`poi-ooxml`) lades till som ett vanligt beroende i
  rotens `pom.xml`** (samma version, 5.2.5, som den gamla modulen
  använde) - första gången POI blir ett riktigt runtime-beroende av den
  deployade jaren, en medveten avvikelse från [ADR 0010](docs/adr/0010-excel-tool-standalone-module.md)s
  ursprungliga motivering (se ADR 0014).
- **`<classifier>exec</classifier>` togs bort från
  `spring-boot-maven-plugin`-konfigurationen i rotens `pom.xml`.** Den
  fanns bara för att `tools/import-excel` skulle kunna bero på
  huvudartefakten som ett vanligt Maven-bibliotek (en Boot-fatjar
  fungerar inte som beroende) - med modulen borttagen finns inget kvar
  som behöver den vanliga, platta jaren. Verifierat efteråt: `mvn package`
  producerar återigen en enda `winecellar-0.1.0-SNAPSHOT.jar` (ingen
  `-exec`-suffix), och `unzip -l` bekräftar att den fortfarande är en
  riktig Spring Boot-fatjar (`BOOT-INF/`-struktur) - att bara ta bort
  `<classifier>` hade i teorin kunnat få `repackage` att sluta binda
  till `package`-fasen om plugin-blocket av någon anledning behövt mer
  konfiguration för att aktiveras, så det kändes värt att verifiera
  konkret istället för att lita på minnet av hur Spring Boots
  Maven-plugin fungerar.
- **`ADR 0010` markerad Superseded av `ADR 0014`** i den här storyn
  (inte i WINE-19/ADR-storyn) - i linje med ADR 0014:s egen instruktion
  om att göra det först när `tools/import-excel` faktiskt tas bort.
- README.md:s "Import och export av Excel-data"-avsnitt krympt till
  bara kolumnlayout-tabellen (fortfarande relevant domänkunskap) + en
  kort not om att CLI-kommandona är borttagna och webbfunktionen byggs
  i WINE-20 till WINE-26 - de gamla PowerShell/Bash-kommandoexemplen
  (miljövariabler, `-Dexec.args`-citattecken-fällan) är borttagna i sin
  helhet, de gäller inte länge.
- Verifierat: `mvn verify` grön (alla enhetstester inklusive de flyttade
  `WineRowParserTest`/`WineRowWriterTest`/`ImageMatcherTest`, samtliga
  acceptans-/UI-tester oförändrade), plus en manuell `mvn package`-
  kontroll av jar-strukturen enligt ovan.

**WINE-21 byggd (2026-07-25): bildnamngivning `<producent>_<namn>_<årgång>`
med fallback.** `ImageMatcher.findImage(...)` bytte signatur från
`findImage(String wineName)` till `findImage(String producer, String
name, Integer vintage)` - försöker i första hand slå upp den
fullständiga identitetsstammen (samma tre fält som dubblettvarningens
identitet, WINE-6) om BÅDE `producer` och `vintage` är satta, och faller
ALLTID tillbaka till namn-bara uppslagning om det första försöket inte
gav träff - oavsett anledning (fältet saknades på raden, eller filen i
mappen råkade följa den äldre namn-bara konventionen). Den bredare
fallback-logiken (försök alltid, inte bara när fält saknas) är
medvetet mer tillåtande än vad storyn ursprungligen efterfrågade, men
kostar inget extra att implementera och gör övergången mjukare för
bildmappar som ännu inte döpts om.
- **Ingen ändring av `fileByWineName`-uppslagstabellen eller
  tvetydighetsvarningen** - båda konventionerna är bara strängar i
  samma karta (nyckel = filnamnsstam), så samma varning vid krockande
  stammar gäller identiskt för `"Barolo.jpg"`/`"Barolo.png"` som för
  `"Pio_Cesare_Barolo_2018.jpg"`/`"Pio_Cesare_Barolo_2018.png"`.
- **`identityFileNameStem(producer, name, vintage)`** är en ny `public
  static`-metod på `ImageMatcher`, tänkt att återanvändas av WINE-23
  (bildexport, ska namnge utskrivna filer med exakt samma konvention).
  Ingen egen klass extraherad för det ännu - bara en enda verklig
  anropsplats (uppslagningen här) finns just nu, samma "vänta med
  abstraktion till ett andra verkligt behov"-princip som redan följs på
  andra ställen i projektet (t.ex. `Databaskoppling`, se Excel-import-
  historiken ovan).
- **Mellanslag i producent/namn ersätts med understreck** i den
  beräknade stammen (`Pio Cesare` → `Pio_Cesare`) - upptäckt under
  testskrivningen, inte bestämt i förväg: ett första testfall skrev
  filen som `Pio_Cesare_Barolo_2018.jpg` men `identityFileNameStem`
  hade då byggt `"Pio Cesare_Barolo_2018"` (bokstavligt mellanslag kvar)
  och testet gav `NullPointerException` på ett `null`-svar. Löst genom
  att lägga till en `withoutSpaces(...)`-hjälpare - i övrigt ingen
  normalisering (skiftläge/diakritiska tecken orörda), samma "exakt
  matchning, ingen gissning"-princip som redan gällde namn-bara
  matchningen.
- **`WineRowWriter` rördes INTE** i den här storyn, till skillnad från
  vad storyns ursprungliga beskrivning antog. Vid närmare granskning
  visade det sig att `WineRowWriter` bara skriver ANKRADE xlsx-bilder
  (`Picture`-objekt i "Bild"-kolumnen) - den skrev aldrig separata
  bildfiler till en mapp, det gjorde den nu borttagna CLI-klassen
  `ExportExcel.skrivBildfiler(...)` (se WINE-20), som inte flyttades in
  i huvudappen. Det finns alltså ännu ingen "skrivare" att uppdatera för
  filnamnskonventionen - `identityFileNameStem(...)` väntar på att
  WINE-23 bygger den funktionaliteten från grunden.
- **`ImageMatcherTest`** fick tre nya testfall (fullständig identitet
  matchar entydigt mellan två samnamniga viner, fallback när
  producent/årgång saknas, fallback när identitetsstammen inte ger
  träff men namnstammen gör det) plus ett för `identityFileNameStem`
  självt - övriga befintliga tester uppdaterade bara sitt anrop till
  den nya tre-parameters-signaturen (`findImage(null, "Barolo", null)`
  istället för `findImage("Barolo")`), ingen ändrad förväntan.
- Verifierat: `mvn verify` grön.

**WINE-22 byggd (2026-07-25): webbaserad export av vinlistan (xlsx).**
Ny `GET /export/xlsx` (`ExportController`, skyddad som alla andra
routes) - skriver bara den inloggade användarens egna viner
(`WineService.listWines(owner)`), sorterade på namn (samma ordning som
den gamla CLI-exporten hade, `ORDER BY name` - inte ett krav från
storyn, men en billig, trogen detalj att behålla), via `WineRowWriter`.
- **`CurrentUser` extraherad ur `WineController.currentOwner(...)`** -
  andra verkliga anropsplatsen (nu `ExportController`) gjorde det värt
  det, samma "vänta med abstraktion till ett andra behov"-princip som
  redan följs i projektet. `WineController.currentOwner(...)` är kvar
  som en tunn delegerande wrapper, oförändrat för alla dess befintliga
  anropsplatser.
- **`WineRowWriter` fick `SHEET_NAME`/`writeHeaderRow(Sheet)`** - flyttat
  hit från den borttagna CLI-klassen `ExportExcel` (samma flikamn "Vin",
  samma rubrikradstexter) eftersom rubrikraden hör till samma delade
  kolumnlayout som resten av klassen redan äger.
- **`ExportControllerTest`** (`@WebMvcTest`) skiljer sig från
  `WineControllerTest`s mönster på en viktig punkt: eftersom svaret är
  binärt (xlsx), räcker inte `content().string(...)`-matchning för att
  verifiera "rätt viner, rätt fältvärden" - testet öppnar de faktiska
  svarsbytes:en med POI:s `WorkbookFactory` och läser tillbaka celler,
  precis som `WineRowWriterTest` redan gör för enskilda rader. En egen
  `Dataisolering`-liknande verifiering (`verify(wineService).
  listWines(eq(ownerId))`) täcker att bara den inloggade användarens
  egna viner efterfrågas.
- **Länk i UI:t:** en enkel `<a href="/export/xlsx">Exportera till
  Excel</a>` bredvid "Lägg till vin" i `vinkallare.html` - ingen egen
  "Importera/exportera"-sida ännu, eftersom import (WINE-24/WINE-25)
  inte finns än; en sådan sida är en naturlig omstrukturering när
  importformuläret byggs, inte något att bygga i förväg här.
- Verifierat manuellt mot en riktig lokal Postgres (docker-compose):
  registrerade ett testkonto, lade till ett vin, hämtade `/export/xlsx`
  - `Content-Disposition`/`Content-Type` korrekta, filen är en giltig
  OOXML-zip (`PK\x03\x04`-magibytes). `mvn verify` grön.

**WINE-23 byggd (2026-07-25): bildexport som zip-nedladdning.** Ny
`GET /export/bilder.zip` (samma `ExportController` som WINE-22, inte en
egen klass - båda hör till samma "export"-koncept) - en fil per vin med
sparad bild hos den inloggade användaren, byggd i farten med
`java.util.zip.ZipOutputStream` (ingen mellanlagring på disk).
- **`ImageMatcher.EXTENSION_BY_MIME` breddad från paketsynlig till
  `public`** - samma "andra verkliga anropsplatsen motiverar bredare
  synlighet"-princip som redan följts några gånger i den här fasen
  (klasserna själva i WINE-20, `identityFileNameStem` i WINE-21).
- **Ny `ImageMatcher.fileNameStem(producer, name, vintage)`** - skrivsidans
  motsvarighet till läsningens `findImage`-fallback, men enklare: skrivsidan
  producerar exakt EN fil per vin och måste därför committa till EN
  bestämd stam (fullständig identitet om både producer och vintage är
  satta, annars bara namnet) - till skillnad från läsningen, som kan
  acceptera ATT BÅDA konventionerna ligger på disk och därför försöker
  båda i tur och ordning.
- **En namnkrock hittad under designarbetet, inte i en bugrapport:**
  två viner med exakt samma namn och utan fullständig identitet (t.ex.
  bägge saknar årgång) skulle råka få samma beräknade filnamnsstam.
  `ZipOutputStream.putNextEntry(...)` tillåter INTE två poster med
  samma namn - ett andra `putNextEntry`-anrop med samma namn kastar
  `ZipException` och hade kraschat hela nedladdningen för en användare
  vars datamängd råkade innehålla den kombinationen. Löst genom att
  hoppa över (inte skriva) en krockande bilds post nummer två och
  framåt - ett `Set<String>` håller reda på redan använda postnamn.
  Ingen varning visas för användaren (till skillnad från `ImageMatcher`s
  konsol-varningar vid import/CLI-körning) - det finns ingen naturlig
  "konsol" att skriva till för en webbnedladdning, och det ansågs för
  litet ett scope att bygga en flash-meddelande-mekanism för i den här
  storyn.
- **`ExportControllerTest`** fick motsvarande zip-tester: rätt
  `Content-Type`/`Content-Disposition`, zip-innehållet öppnas med
  `java.util.zip.ZipInputStream` och verifieras innehålla EXAKT en post
  (för vinet med bild, rätt namngiven enligt konventionen) - vinet utan
  bild bidrar ingen post, plus en `UtanInloggning`-nekandetest.
  Testbilden är samma kända 1x1-PNG som redan används i
  `LabelScanFormIT`.
- Verifierat manuellt mot en riktig lokal Postgres: registrerade ett
  konto, laddade upp ett vin med bild, hämtade `/export/bilder.zip` -
  `unzip -l` visade exakt en fil, namngiven `Pio_Cesare_Barolo_2018.png`
  (bekräftar hela namnkonventionskedjan: producent+namn+årgång,
  mellanslag ersatta med understreck), och den uppackade filen var
  byte-identisk med originaluppladdningen (`cmp`). `mvn verify` grön.

**WINE-24 byggd (2026-07-25): webbaserad import - torrkörning/
förhandsgranskning, INGET sparas.** Ny `GET/POST /import`
(`ImportController`) och `application.ImportPreviewService` (ny,
`@Service` - kategoriserar redan tolkade `Wine`-kandidater mot
`WineService.checkForDuplicate`, samma orkestrering-hör-hemma-i-
applikationslagret-princip som ADR 0006). Fem sammanfattningstal (rader
totalt, hoppade-över, fullständiga/partiella dubbletter, rena nya) -
bara siffror, ingen radvis lista, matchar exakt vad storyn efterfrågade.

**En design-diskussion innan kodningen ändrade lösningen väsentligt,
inte bara detaljer.** Ursprungsplanen (lagra uppladdade bilder rakt i
HTTP-sessionen mellan torrkörning och commit) ifrågasattes av
användaren: "kommer det att fungera med 100 bilder som kanske inte är
särskilt komprimerade?". Två verkliga problem identifierade innan någon
kod skrevs:
1. **`application.yml`s multipart-gräns var redan `max-request-size:
   5MB`** (satt för "ett foto", se `WINECELLAR_LOCAL_IMAGE_FOLDER`-
   eran) - en enda bulk-request med ~100 okomprimerade telefonfoton
   (ofta 3-8 MB styck) hade avvisats direkt av Spring, långt innan
   sessionslagring ens blev relevant.
2. **HTTP-sessionen här är Tomcats vanliga in-minnet-lagring** (inget
   Spring Session/Redis) - tiotals-hundratals MB bilddata per
   pågående import hade legat kvar i JVM-heapen så länge sessionen
   levde.

**Lösning, båda delarna byggda i den här storyn:**
- **Klientsidans Canvas-nedskalning** (`import.html`) - samma teknik
  som etikettskanningens `etikett-input` (WINE-5, `vin-formular.html`),
  men utökad till att hantera POTENTIELLT MÅNGA filer samtidigt (hela
  `webkitdirectory`-mappen) via `Promise.all(...)`, med
  skicka-knappen inaktiverad och ett statusmeddelande
  ("Komprimerar N bilder...") medan nedskalningen pågår - annars hade
  ett tidigt klick på "Förhandsgranska" skickat de OSKALADE
  originalfilerna innan de async Canvas-anropen hunnit bli klara. Varje
  fils ORIGINALA filnamn behålls (bara innehållet komprimeras) -
  servern matchar bilder mot viner via filnamnet (WINE-21), inte
  filinnehållet. Icke-bildfiler (t.ex. systemfiler i den valda mappen)
  lämnas orörda och skickas igenom oförändrade - servern ignorerar
  ändå okända filändelser.
- **`max-file-size`/`max-request-size` höjda till 10MB/50MB** - även
  efter klientsidans nedskalning (typiskt ~100-300 KB per bild) ger
  det marginal för ~100 bilder i en och samma request, samtidigt som
  `max-file-size` fortfarande skyddar mot en orimligt stor enskild fil
  om JS av någon anledning inte kört.
- **Uppladdad xlsx + bildmapp skrivs till en temporär mapp PÅ DISK**
  (`Files.createTempDirectory("winecellar-import-")`) - bara
  mappsökvägen (en kort sträng, inte bilddatan) hålls i
  `HttpSession` (`ImportController.SESSION_KEY_PENDING_IMPORT_PATH`),
  så JVM-heapen belastas inte av hur mycket data en pågående import
  faktiskt innehåller, oavsett om nedskalningen ovan av någon
  anledning inte skulle räcka till.
- **WINE-27 skapad och länkad (`depends on` WINE-24) under samma
  diskussion** - städning av övergivna temp-mappar (en användare som
  aldrig bekräftar sin import lämnar en mapp kvar på disk) är en
  medvetet UPPSKJUTEN, egen story snarare än något löst i förbifarten
  här - användarens eget förslag, för att hålla WINE-24:s scope till
  bara torrkörningen.
- **Upptäckt under den manuella verifieringen (inte planerat i
  förväg):** `ImportControllerTest` (som INTE mockar bort
  `stashUploadForCommit`, bara `WineService`/`UserRepository`) skapar
  riktiga temp-mappar på disk vid varje testkörning - samma
  "orphaned temp dir"-problem som WINE-27 ska lösa för riktiga
  användare uppstår alltså redan av att köra testsviten upprepade
  gånger. Bekräftar att WINE-27 är ett verkligt, nära förestående
  behov, inte ett hypotetiskt framtida.
- **Varje radfel (saknat namn, men även t.ex. ett okänt betygsvärde)
  räknas som "hoppas över" via ett brett `catch (RuntimeException e)`
  runt varje rads parsning** - medvetet bredare än storyns bokstavliga
  "saknar namn"-formulering, för att EN trasig rad inte ska stoppa hela
  torrkörningen för alla andra giltiga rader. En felaktig xlsx-fil (fel
  flikamn, helt oläsbar) fångas separat på hela-filen-nivå och visar
  ett vänligt felmeddelande istället för att krascha.
- **`ImportControllerTest`** bygger en riktig, minimal xlsx i minnet
  med POI (fyra rader: fullständig dubblett, partiell dubblett, ren,
  och en rad utan namn) istället för att checka in en testfil - mockar
  `WineService.checkForDuplicate` via en `Answer` som växlar på vinets
  namn (robustare än att försöka träffa en exakt `equals()`-matchad
  `Wine`-instans för varje stubbning). `@Import({SecurityConfig.class,
  ImportPreviewService.class})` - den riktiga `ImportPreviewService`
  körs (bara dess `WineService`-beroende mockas), inte en egen mock av
  hela tjänsten, eftersom det är just orkestreringen (parsning →
  kategorisering) som ska verifieras.
- **Formuläret för att välja dubblettstrategi (`import.html`, efter en
  lyckad torrkörning) postar redan mot `/import/commit`** - den routen
  finns INTE än (WINE-25). Medvetet: nästa story bygger commit-steget
  direkt ovanpå det här, ingen anledning att skjuta upp fältnamnen.
- Verifierat: `mvn verify` grön, plus en manuell end-to-end-rundtur mot
  en riktig lokal Postgres - exporterade ett riktigt sparat vin,
  laddade upp samma fil via `/import`, sammanfattningen visade
  "1 fullständig dubblett" korrekt, och vinlistan innehöll fortfarande
  bara det ursprungliga vinet efteråt (inget sparades).

**WINE-25 byggd (2026-07-26): webbaserad import - commit-steget som
faktiskt sparar.** Ny `POST /import/commit`, samma `ImportController`
som WINE-24. Läser tillbaka den torrkörda filen/bildmappen från
temp-mappen (ingen ny uppladdning krävs - hela poängen med
temp-mappmekanismen från WINE-24), tillämpar den valda
dubblettstrategin per rad, sparar via `WineService.save(...)`/
`increaseQuantity(...)`, och städar bort temp-mappen efteråt.
- **Post-Redirect-Get med flash-attribut, inte en direkt rendering av
  resultatet.** Till skillnad från torrkörningen (som är ofarlig att
  köra om - den sparar ju ingenting, så `POST /import` kan gott rendera
  om samma sida direkt) hade en siduppdatering efter ett direkt-
  renderat commit-svar orsakat en NY, dubblerande import-körning (ett
  reellt korrekthetsproblem, inte bara kosmetiskt) om användaren
  råkade trycka F5. Löst med `RedirectAttributes.addFlashAttribute(
  "result", ...)` + `redirect:/import` - första gången det mönstret
  används i projektet (övriga POST-hanterare antingen redirectar utan
  meddelande, eller renderar om samma formulär direkt för
  validerings-/dubblettvarningar som ÄR ofarliga att repetera).
  Spring märger automatiskt in flash-attributet i nästa `GET /import`s
  modell utan att `importForm()`-metoden behöver deklarera en egen
  `Model`-parameter för det - `FlashMap`-mekanismen sker på ramverksnivå
  innan handlern körs.
- **`parseRows` refaktorerad till att ta emot en `InputStream` direkt**
  (inte en `MultipartFile`) - delas nu mellan torrkörningen (läser från
  den uppladdade filen) och commit-steget (läser tillbaka samma fil
  från temp-mappen) - måste tolka exakt likadant båda gångerna, en
  delad metod eliminerar risken att de två glider isär (samma princip
  som `COL_*`-konstanterna/`EXTENSION_BY_MIME` på andra ställen i den
  här fasen).
- **Bildmatchning återanvänder `ImageMatcher`** (WINE-21) direkt mot
  temp-mappens `bilder`-undermapp - `findImage(producer, name, vintage)`
  anropas per kandidatrad precis innan sparning, bilden bäddas in i
  `Wine.Builder` exakt som det manuella formuläret redan gör.
- **Ingen egen `application`-tjänst för commit-orkestreringen**, till
  skillnad från torrkörningens `ImportPreviewService` (WINE-24) - valt
  medvetet för konsekvens med `WineController`s redan etablerade mönster,
  där den ENSKILDA dubblettvarningens upplösning (`confirmAdd`,
  `dubblett-oka-antal`-routen) också ligger direkt i controllern, inte i
  `WineService`. Bulk-commit-strategin är i grunden samma sorts
  "webblagret tolkar formulärval och orkestrerar redan befintliga
  `WineService`-anrop"-logik, bara upprepad per rad.
- **Två nya enum:er, `FullDuplicateStrategy`/`PartialDuplicateStrategy`**
  (paketprivata nästlade enum:er i `ImportController`, bundna direkt via
  Spring precis som `SortField`/`SortDirection` redan binds i
  sök-/sorteringsformuläret) - värdena matchar exakt `import.html`s
  `<option value="...">`-attribut från WINE-24 (byggda i förväg för att
  matcha det här steget).
- **Testfälla hittad av `mvn test`, inte manuellt: två nya
  MockMvc-tester antog fel att `wineService.save(...)` skulle anropas
  NOLL respektive EN gång.** Testfilens fjärde rad ("Rioja") är
  medvetet en ren, icke-dubblett rad i ALLA scenarier (samma testfil
  återanvänds för alla dubblettstrategi-kombinationer) - den sparas
  alltså ALLTID, oavsett vilken fullständig/partiell-dubblettstrategi
  som testas för de ANDRA raderna. Ett test som valde "hoppa över" för
  både fullständiga och partiella dubbletter förväntade sig då NOLL
  `save()`-anrop totalt, men fick ETT (Rioja); ett annat som valde
  "lägg till som nytt" för partiella dubbletter förväntade sig ETT
  `save()`-anrop (bara "Chianti") men fick TVÅ (Chianti + Rioja).
  Fixat genom att räkna med Rioja i förväntningarna istället för att
  ändra testdatan - testfilens fyra rader (full dubblett, partiell
  dubblett, ren, saknar namn) var redan en medveten, minimal
  täckning av alla fyra kategorierna och borde inte behöva bytas ut.
- **`ImportControllerTest`s dubblettmockning fick riktiga
  `Wine`-fixturer MED `id`** (`EXISTING_BAROLO`/`EXISTING_CHIANTI`,
  `Wine.WineId(101L)`/`(102L)`) istället för att eka tillbaka
  kandidatvinet självt som "existing" - kandidatvinet (nytolkat av
  `WineRowParser`) har alltid `id() == null`, så
  `wineService.increaseQuantity(existing.id(), owner)` hade fått ett
  `null`-id om testet naivt återanvänt kandidaten som sitt eget
  "befintliga vin"-mock-svar. En lärdom värd att komma ihåg för
  framtida dubblett-relaterade tester: ett tolkat kandidatvin och ett
  redan sparat "existing"-vin är aldrig samma objekt i verkligheten,
  och bör inte heller vara det i en mock.
- Verifierat manuellt, end-to-end mot en riktig lokal Postgres:
  registrerade ett konto, lade till "Barolo" (3 flaskor), exporterade,
  laddade upp samma fil via `/import`, valde "öka antal" för
  fullständiga dubbletter, committade - resultatsidan visade korrekt
  "1 fick antalet ökat", vinlistan innehöll fortfarande bara ett vin,
  och dess flaskantal hade verkligen ökat från 3 till 4 i databasen.
  Bekräftat att temp-mappen för DEN körningen togs bort efter commit
  (kvarvarande temp-mappar efteråt härrörde uteslutande från
  testsvitens egna torrkörnings-bara scenarier, som aldrig committar -
  exakt det WINE-27 ska lösa, inte ett tecken på att städningen i den
  här storyn brast). `mvn verify` grön.

**WINE-26 byggd (2026-07-26): Playwright-täckning för hela import-/
exportflödet - och en riktig bugg hittad av just det, som varken
MockMvc eller curl-baserad manuell verifiering någonsin kunde ha
avslöjat.** Ny `ImportExportFlowIT` (samma `@SpringBootTest`+
Testcontainers-mönster som `WineListResponsiveIT`/`LabelScanFormIT`):
konto A lägger till ett vin med bild, exporterar både `.xlsx` och
bildzip via riktiga nedladdningar (`Page.waitForDownload(...)`), ett
HELT ANNAT, tomt konto B laddar upp samma filer via `/import`
(`webkitdirectory`-inputen får en MAPPSÖKVÄG, inte enskilda filer - se
fälla nedan), kör torrkörningen, bekräftar, och kontot B:s vinlista
verifieras innehålla vinet med en fungerande bild efteråt.

- **Ingen Cucumber-scenario byggdes för de återstående
  dubblettstrategikombinationerna, trots att storyn bad om det -
  en medveten avvikelse, upptäckt vid närmare eftertanke innan
  kodning.** Den faktiska "vilken WineService-metod anropas för vilken
  dubbletttyp+strategi"-logiken bor i `ImportController` (en medveten
  WINE-25-design, samma mönster som `WineController`s egen
  duplicate-varning-hantering) - det finns ingen application-lagers-
  tjänst för commit-orkestreringen att skriva ett Cucumber-scenario
  MOT. De tre återstående kombinationerna (fullständig dubblett +
  hoppa över, partiell dubblett + öka antal, partiell dubblett + hoppa
  över - WINE-25s egna tester täckte bara fullständig+öka-antal och
  partiell+lägg-till-som-nytt) lades istället till som tre nya
  `@Test`-metoder i `ImportControllerTest` (samma MockMvc-nivå som
  redan testar resten av importflödet) - rätt testlager för just den
  här logiken, även om det avviker från storyns bokstavliga
  ordalydelse.
- **Playwrights `setInputFiles` på en `webkitdirectory`-input KRÄVER en
  mappsökväg, inte en lista med enskilda filsökvägar** - ett första
  försök att skicka in den uppackade bildfilens sökväg direkt gav
  `PlaywrightException: [webkitdirectory] input requires passing a
  path to a directory`. Löst genom att packa upp zip-filen till en EGEN
  temporär mapp och skicka in MAPPEN till `setInputFiles` istället -
  Playwright laddar då upp alla filer den innehåller, precis som en
  riktig mappväljare skulle göra. Värt att komma ihåg för framtida
  Playwright-tester mot `webkitdirectory`-inputar.
- **Riktig produktionsbugg hittad (inte en testbugg): klientsidans
  Canvas-nedskalning (`import.html`, WINE-24) skrev alltid om
  bildinnehållet till JPEG, men behöll bildens URSPRUNGLIGA filnamn
  (inklusive dess ursprungliga ändelse, t.ex. `.png`).**
  `ImageMatcher.findImage(...)` bestämmer MIME-typ utifrån filens
  ÄNDELSE (inte dess faktiska innehåll, se `MIME_BY_EXTENSION`) - en
  fil som fortfarande hette `Pio_Cesare_Barolo_2018.png` men vars bytes
  nu var en JPEG-ström fick alltså MIME-typen `image/png` felaktigt
  rapporterad till webbläsaren, trots att innehållet var JPEG. Det
  första testförsöket (som antog byte-identisk rundtripp, se nedan)
  avslöjade detta indirekt via en `Content-Type: image/png`-header som
  inte stämde med de faktiska JPEG-magibytesen. **Ingen tidigare
  verifiering kunde ha hittat den här buggen** - `ImportControllerTest`
  (MockMvc) skickar redan-nedskalade testbilder direkt utan att någon
  webbläsare/JS är inblandad, och `ExportControllerTest`/den manuella
  curl-baserade verifieringen (WINE-23) testar bara EXPORT-riktningen,
  som aldrig skriver om bildinnehåll. Bara en RIKTIG webbläsare som
  faktiskt kör den riktiga nedskalnings-JS:en (den här storyn) kunde
  avslöja missmatchningen. Fixat i `import.html`: filnamnets STAM
  behålls (så `ImageMatcher`s namnmatchning fortfarande fungerar), men
  ändelsen byts uttryckligen till `.jpg` för att spegla vad `duk.toBlob`
  faktiskt skrev.
- **Testets bildjämförelse är medvetet INTE byte-identisk** (till
  skillnad från WINE-23s curl-baserade, JS-fria verifiering av
  EXPORT-sidan) - eftersom klientsidans nedskalning ALLTID skriver om
  bilden till en komprimerad JPEG, är en förlustfri rundtripp genom
  `/import` inte ens avsett att vara byte-identisk. Testet verifierar
  istället att en bild kommer fram över huvud taget, kopplad till rätt
  vin, med rätt (nu korrekta) `Content-Type`.
- Verifierat: den nya `ImportExportFlowIT` grön i isolering
  (`failsafe:integration-test -Dit.test=ImportExportFlowIT`, fyra
  körningar innan den blev grön - se fällorna ovan för de tre
  bakomliggande problemen), och `mvn verify` grön i sin helhet
  efteråt.

**[ADR 0015](docs/adr/0015-bulk-import-images-lossy-jpeg.md) skriven
2026-07-26, på användarens initiativ** - fångar formellt beslutet (redan
byggt i WINE-24/verifierat av WINE-26 ovan) att bulkimportens bilder
medvetet INTE rundtrippar bit-exakt (klientsidans Canvas-nedskalning
skriver alltid om till JPEG). Användaren påpekade att en export följt
av en re-import därför inte ger tillbaka samma bildbytes, och att det
förtjänade en egen ADR snarare än att bara stå som en rad i den här
kronologiska loggen - eftersom det är ett bestående, avsiktligt
avsteg från "full rundtripp" som framtida sessioner annars lätt kan
missta för en bugg. Bekräftat i samma veva: den vanliga
"Etikett"-filuppladdningen (ett vin i taget, `vin-formular.html`) och
etikettskanningens (WINE-5) LLM-tolkning är BÅDA opåverkade - ingen
nedskalning sparas någonsin som vinets bild i de flödena, bara
bulkimportens `webkitdirectory`-väg gör det.

**WINE-29 (2026-07-27): transparens bevaras vid bulkimport - ADR 0015
uppdaterad, inte reverserad.** En bild med transparent bakgrund tappade
sin transparens helt vid bulkimport (rapporterad bugg) - en direkt,
förutsägbar konsekvens av att ADR 0015:s ursprungliga beslut alltid
skrev om till JPEG, som helt saknar alfakanal. Avstämt med användaren
innan kodning (se ADR 0015s eget "Tillägg 2026-07-27"-avsnitt för den
fulla motiveringen) - **inte** en reversering av det ursprungliga
beslutet, bara ett villkorat undantag för bilder som faktiskt har
transparens.
- **`import.html`s Canvas-kod skannar nu alfakanalen** (`getImageData`,
  leta efter alfavärde < 255) efter nedskalningen, innan `toBlob`
  anropas. Helt ogenomskinliga bilder är HELT oförändrade (samma
  `image/jpeg`, kvalitet 0.85, som innan) - bara bilder med minst en
  transparent/halvtransparent pixel begär `'image/webp'` istället.
- **Filändelsen läses av EFTER `toBlob`, från blobbens FAKTISKA
  `type`** (`ÄNDELSE_PER_MIME`-uppslagning), inte hårdkodad i förväg -
  eftersom HTML-specen kräver att en webbläsare som inte kan koda WebP
  MÅSTE falla tillbaka till PNG (aldrig JPEG, aldrig `null`), och vilket
  av de två som faktiskt kom tillbaka avgörs alltså av webbläsaren, inte
  av vår kod. Samma klass av bugg som WINE-26 en gång fixade (ändelse
  som inte stämde med det faktiska innehållet) skulle annars kunna
  återuppstå i det nya PNG/WebP-fallet.
- **Ingen serverändring behövdes** - `ImageMatcher` kände redan igen
  både `webp` och `png` (`MIME_BY_EXTENSION`/`EXTENSION_BY_MIME`,
  byggda i tidigare stories).
- **Verifiering krävde en RIKTIG webbläsare, inte Java-avkodning** -
  JVM:ens `ImageIO` saknar inbyggt WebP-stöd (samma typ av begränsning
  som redan gäller för `WineRowWriter`s xlsx-inbäddning, se
  Excel-import-avsnittet: "Inte WEBP"). `ImportExportFlowIT` fick ett
  nytt scenario som istället kör `Page.evaluate(...)` i webbläsaren
  själv: hämtar den importerade bilden via `fetch`, ritar den på en
  `OffscreenCanvas` via `createImageBitmap`, och läser tillbaka
  alfavärdet direkt i webbläsarkontexten - format-agnostiskt (fungerar
  oavsett om resultatet blev WebP eller PNG-fallbacken) och testar det
  faktiska felet (tappad transparens), inte bara `Content-Type`.
- **Ny testbild krävdes:** den befintliga `EN_PIXEL_PNG`-fixturen
  (`ImportExportFlowIT`, delad med `LabelScanFormIT`) visade sig vid
  närmare granskning vara grayscale+alfa (PNG-färgtyp 4) men med
  alfavärde 255 - alltså tekniskt alfakapabel men i praktiken helt
  ogenomskinlig, så den triggar inte den nya kodvägen. En ny
  `HALVTRANSPARENT_PIXEL_PNG`-fixtur (1x1 RGBA, alfa 128) byggdes
  istället - `läggTillVinMedBild(...)` gjordes parametriserbar
  (bild-bytes/filnamn/mime-typ) så båda testbilderna kan återanvända
  samma uppladdningshjälpare.
- Verifierat: `ImportExportFlowIT` grön i isolering (båda scenarierna -
  det befintliga opaka fallet är oförändrat, det nya transparenta
  fallet grönt), och `mvn verify` grön i sin helhet efteråt.

**WINE-32 byggd (2026-07-27): "Bild"-kolumnen i Excel-import/export
borttagen helt - ADR 0011 markerad Deprecated (inte reverserad, den
beskriver bara ett beslut som inte längre är relevant).** Kolumnen
(I, `WineRowParser.COL_IMAGE`) lästes redan aldrig vid import - den
enda kvarvarande användningen var att `WineRowWriter` skrev en ankrad
POI-`Picture` dit vid export, en ren visuell bekvämlighet som aldrig
var en del av den faktiska bildrundtrippen (den går via
`/export/bilder.zip`, se ADR 0014/WINE-23). Eftersom mekanism 2 i
ADR 0011 (den delade lokala bildmappen) redan försvann i WINE-20/23
fanns ingen kvarvarande anledning att behålla den sista xlsx-specifika
bildvägen heller.
- **Kolumnen togs bort helt ur layouten, inte bara tömdes** - layouten
  gick från A-V (22 kolumner) till A-U (21). `WineRowParser.COL_IMAGE`
  togs bort och alla kolumner efter den (Inköpsdatum och framåt)
  skiftade ett steg åt vänster. Samma sorts mekaniska indexskifte som
  när Systembolagets prodnummer en gång sköt in en NY kolumn (se
  Excel-import-avsnittet ovan) - fast i motsatt riktning.
- **`WineRowWriter`:** `image(...)`-metoden, `"Bild"` ur `HEADERS`,
  `POI_PICTURE_TYPE_BY_MIME`-kartan och det `Drawing<?> drawing`-
  parametret på `write(...)` togs bort helt - `ExportController`
  slutade därmed också behöva `sheet.createDrawingPatriarch()`.
- **Två tester i `WineRowWriterTest` togs bort** (inte bara justerade)
  - de testade specifikt bildinbäddningen (byte-identisk PNG, webp
    hoppas över med varning), en funktion som inte längre finns.
    Kvarvarande tester i `WineRowParserTest`/`WineRowWriterTest`/
    `ImportControllerTest` fick sina hårdkodade kolumnindex justerade
    till den nya layouten - inga nya tester behövdes (matchar
    storyns eget acceptanskriterium).
- **ADR 0011 markerad Deprecated, med en konkret motivering skriven
  in i ADR:n själv** (inte bara en hänvisning till story-numret,
  på uttrycklig begäran från användaren) - båda mekanismerna beslutet
  en gång beskrev är nu borta, och det finns ingen efterträdande ADR
  eftersom det här är en ren avveckling, inte ett nytt beslut.
- Verifierat: `mvn verify` grön (106 enhets-/webblagertester,
  50 IT-tester).

**WINE-27 byggd (2026-07-28): övergivna temp-importmappar städas nu,
en diskussionsstory (som WINE-9/WINE-19) avstämd med användaren innan
kodning.** [ADR 0017](docs/adr/0017-login-triggered-temp-import-cleanup.md)
fångar det arkitektoniska valet - kort sammanfattat: två alternativ
diskuterades (schemalagd bakgrundsuppgift kontra att haka på ett
redan existerande, återkommande händelseflöde), och valet föll på det
senare: en lyckad inloggning (`InteractiveAuthenticationSuccessEvent`)
triggar ett svep av alla `winecellar-import-*`-mappar äldre än 2 timmar
i OS-temp-katalogen. Motiveringen (ingen ny bakgrundsmekanism för ett
lågriskproblem som bara handlar om diskstädning) finns i ADR:n, inte
här.
- **En andra, mer akut läcka hittades under kodgranskningen, inte
  nämnd i storyns ursprungliga beskrivning:** `ImportController.
  preview()` (torrkörningen) skapade tidigare en HELT NY temp-mapp vid
  varje anrop, även om sessionen redan hade en overkommitterad från en
  tidigare torrkörning - den gamla sökvägen skrevs bara över i
  sessionen, mappen blev övergiven direkt (inte bara vid ett
  övergivet-helt-flöde). Fixat genom att ta bort en eventuell
  tidigare, ej committerad temp-mapp INNAN en ny skapas
  (`deletePreviousPendingImport`) - oberoende av
  `PendingImportCleanup`, som bara är säkerhetsnätet för mappar från
  sessioner som aldrig kom tillbaka alls.
- **Ny klass `PendingImportCleanup`** (`web`-paketet, paketprivat, samma
  synlighetskonvention som `CurrentUser`) - tar emot `tempRoot` som
  konstruktorargument (produktionskoden använder
  `System.getProperty("java.io.tmpdir")`, testerna en JUnit
  `@TempDir`) och `now` som metodargument till
  `cleanupAbandonedImports(Instant)` - båda medvetet injicerade istället
  för att läsas direkt i metoden, för att göra klassen testbar utan att
  röra den riktiga OS-temp-katalogen eller förlita sig på riktiga
  sömnar för att simulera en gammal mapp.
- **`ImportController.TEMP_DIR_PREFIX`** extraherades till en delad,
  paketsynlig konstant (tidigare en bokstavlig sträng inline i
  `Files.createTempDirectory(...)`) - `PendingImportCleanup` refererar
  samma konstant istället för att duplicera strängen, samma
  "en delad källa till sanning"-princip som `WineRowParser.COL_*`
  redan följer för Excel-kolumner.
- **Testfälla hittad under testskrivningen:** ett första försök i
  `PendingImportCleanupTest` satte en testmapps ändringstid till 3
  timmar bakåt och skrev SEDAN en fil i den - vilket i praktiken
  uppdaterade mappens mtime tillbaka till "nu" igen (att skriva en fil
  i en katalog rör katalogens egen ändringstid på de flesta
  filsystem), så det simulerade åldern upphävdes tyst och testet
  failade. Fixat genom att skriva filen FÖRST och sätta
  `FileTime`/`Files.setLastModifiedTime` sist.
- **`ImportControllerTest`** fick en ny `körTorrkörning(MockHttpSession,
  MockMultipartFile...)`-overload (den befintliga varianten utan
  session delegerar nu till den med en ny `MockHttpSession`) för att
  kunna köra två torrkörningar i EXAKT samma session och verifiera att
  den första temp-mappen verkligen försvinner när den andra körs.
- Verifierat: `mvn verify` grön (110 enhets-/webblagertester,
  50 IT-tester).

**WINE-30 byggd (2026-07-29): bildnamngivning använder partiell
identitet, inte bara fullständig identitet eller namn-ensamt.**
Buggen: `ImageMatcher.findImage` försökte tidigare bara den
fullständiga identitetsstammen (`<producent>_<namn>_<årgång>`) när
BÅDA `producer` och `vintage` var satta - annars föll den direkt
tillbaka till namn-bara matchning. Två viner med samma namn men OLIKA
partiell identitet (t.ex. ett med bara producent satt, ett annat med
bara årgång) förväntade sig då båda samma namn-bara bildfil, trots att
de inte alls behöver vara samma vin.
- **Ny regel:** `ImageMatcher.fileNameStem`/`findImage` bygger stammen
  av VILKA fält som faktiskt är satta (producent/namn/årgång),
  separerade med understreck - namn är det enda obligatoriska
  fragmentet. `findImage` provar den mest specifika stammen vinets
  satta fält tillåter först, och faller alltid tillbaka till namn-bara
  matchning om det inte gav träff - håller bakåtkompatibiliteten med
  äldre bildfiler/rader där bara namnet är känt (ADR 0005).
- **Kodgranskning (av Claude, på användarens begäran) hittade två
  saker i den första implementationen som fixades i en uppföljande
  commit:** `ImageMatcher.identityFileNameStem` (den gamla
  fullständig-identitet-metoden) hade blivit dödkod - `fileNameStem`
  byggde nu stammen själv istället för att anropa den, men den gamla
  metoden och dess dedikerade test lämnades kvar oanvända. Samt:
  README.md:s exportavsnitt beskrev fortfarande den GAMLA regeln
  ("bara `<namn>` om producent ELLER årgång saknas"), vilket nu var
  direkt felaktigt eftersom namn+årgång (utan producent) ger
  `<namn>_<årgång>`, inte bara `<namn>`. Båda fixades: dödkoden togs
  bort helt, README.md och ADR 0014 uppdaterades att beskriva alla
  fyra fallen korrekt.
- **Uppföljningen gick längre än granskningens frågor också:**
  mellanslag inom producent-/vinnamn bevaras nu (tidigare ersattes de
  med understreck via en `withoutSpaces`-hjälpare, som försvann i
  samma städning) - bara separatorn MELLAN fälten är understreck, t.ex.
  `Château Margaux_Pauillac Rouge_2015` istället för
  `Château_Margaux_Pauillac_Rouge_2015`. Ingen ökad kollisionsrisk
  jämfört med innan (samma "bara ett fält som redan innehåller ett
  bokstavligt understreck kan krocka"-begränsning som redan fanns).
- **Ny testtäckning utöver det granskningen efterfrågade:** ett
  enhetstest för mellanslagsbevarande med flerordiga fält i alla tre
  positionerna, ett nytt `ExportControllerTest`-scenario för
  producent+namn-utan-årgång, och - viktigast - ett helt nytt
  integrationstest i `ImportControllerTest` som verifierar att en
  uppladdad bild faktiskt kopplas till rätt vin genom HELA
  importflödet för ett partiellt identitetsfall, inte bara isolerat
  mot `ImageMatcher` i ett enhetstest.
- Verifierat: `mvn verify` grön (121 enhets-/webblagertester,
  50 IT-tester).

## Kända fällor att vara uppmärksam på (ärvda från roombooking, kan återkomma)

- **Gherkin på svenska kräver `# language: sv`** som absolut första rad i
  varje `.feature`-fil.
- **Cucumber Expressions skiljer sig från reguljära uttryck på ett sätt
  som ger förvirrande felmeddelanden, inte bara "hittar ingen match".**
  Upptäckt när `sortera-viner.feature`s steg byggdes (2026-07-21):
  `@När("... i (stigande|fallande) ordning")` (regex-stil alternation med
  `|`) matchade tyst ingenting alls - Cucumber tolkar `|` bara som en
  vanlig bokstav i en Cucumber Expression, inte som alternation, så
  felet såg ut som att steget helt saknade en definition (samma
  `UndefinedStepException` som ett riktigt saknat steg hade gett).
  Cucumber Expressions egen alternationssyntax använder `/` istället
  (`(stigande/fallande)`) - men **den** kastade i sin tur ett tydligt
  fel ("An alternation can not be used inside an optional") eftersom
  parenteser i Cucumber Expressions betyder *valfri text*, inte en
  fångstgrupp - en alternation får inte ligga direkt inuti en valfri
  grupp. Löst genom att helt undvika alternation: två separata
  `@När`-metoder (en för "... i stigande ordning", en för "... i
  fallande ordning"), som båda anropar samma privata `sortera(...)`-
  hjälpmetod - enklare och garanterat korrekt, istället för att fortsätta
  brottas med Cucumber Expression-syntaxen. Värt att komma ihåg för
  framtida steg med den här sortens "antingen X eller Y"-text i själva
  Gherkin-meningen.
- **Två stegklasser som delar samma Gherkin-steg (t.ex. samma "Givet
  att källaren innehåller följande viner:") måste vara EN klass, inte
  två - annars pratar de med olika `WineService`-instanser inom samma
  scenario.** Cucumber-JVM skapar (utan en DI-container inkopplad, vilket
  det här projektet inte har) en ny instans av VARJE stegklass per
  scenario, och kör ALLA `@Before`-hooks från ALLA klasser vars steg
  förekommer i scenariot - om `SteKlassA` och `SteKlassB` båda har sin
  egen `@Before` som gör `wineService = new WineService(new
  InMemoryWineRepository())`, blir det två separata repository-instanser
  även inom samma scenario. Ett vin sparat via ett `Givet`-steg i
  `SteKlassA` skulle då vara osynligt för ett `När`-steg i `SteKlassB`.
  Löst genom att lägga sorterings- och filtreringsstegen i en och samma
  klass (`SökOchFilterSteps`, se sammanslagningen 2026-07-21 av det som
  tidigare hette `SorteraVinerSteps`) istället för en klass per
  `.feature`-fil (mönstret `ListaVinerSteps`/`RedigeraVinSteps`/... följer
  annars). Alternativet (konstruktorinjicerad delad "world"-klass, som
  Cucumber-JVM:s inbyggda PicoContainer löser automatiskt) hade också
  fungerat men introducerar ett helt nytt mönster i testkoden - inte värt
  det för två så nära besläktade stegklasser.
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
  utan att begränsa till en enskild motor (se README:s "Köra tester") så
  slipper man den överraskningen.
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
  via `-Dexec.args`, se README:s "Import och export av Excel-data".
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

**Kommande arbete spåras i YouTrack (WINE-projektet), inte som en
checklista i README.md (ändrat 2026-07-22, WINE-1)** - README.md:s
tidigare "Nästa steg"-sektion är borttagen. Backlog/Develop/Review-delen
av flödet drivs av `.claude/skills/plocka-nasta/SKILL.md`.
