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
