# Utvecklingslogg (winecellar)

Detaljerad, kronologisk logg över AI-assisterat arbete i det här repot -
story för story (YouTrack, WINE-projektet), inklusive resonemang,
återvändsgränder, verifieringssteg och detaljer som inte är
arkitektoniska nog för en egen ADR (`docs/adr/`). Historiska poster
nedan är **medvetet inte uppdaterade** när senare arbete ändrar
slutsatsen - varje post speglar vad som var sant/korrekt när den
skrevs, inte nuläget. Lita på `git log`, den faktiska koden, eller
`CLAUDE.md` (som beskriver nuläget) för vad som gäller idag.

**Den här filen laddas inte automatiskt i Claude Code-sessioner** -
läs den vid behov (t.ex. för att förstå varför ett visst vägval gjordes
i detalj). Återkommande, generella lärdomar som är värda att komma ihåg
även utan att läsa hela historiken finns istället i `CLAUDE.md`s "Kända
fällor"-avsnitt.

## Domänmodell - vägval och historik

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
  `updateWine`.** Domänlagret är tunt - det finns ingen skillnad
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
  **Not (senare ändrat): `quantity` blev åter obligatoriskt 2026-07-26,
  se ADR 0016 - det här stycket beskriver bara `name`-delen av 0005,
  som fortfarande gäller.**
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
  designomgång" nedan). Verifierat med Playwright-skärmdumpar i båda
  breddlägena: mobil får ett litet, jämnt andrum utan att desktop-vyn
  påverkas alls.
- **Filtrering/sökning/sortering orkestreras i `WineService`, inte i
  `WineController`** (byggt 2026-07-21, sortering först). Beslutet togs
  medvetet efter en explicit avvägning: Gherkin-/Cucumber-scenarierna
  testar redan mot applikationslagret, inte mot HTTP (se
  `CucumberSpringConfiguration`s kommentar) - hade orkestreringen legat
  i controllern hade scenarier om sortering inte haft något naturligt
  ställe att anropa in på utan att gå via MockMvc/riktig HTTP.
  `WineControllerTest` (`@WebMvcTest` + `@MockBean WineService`)
  påverkades inte av valet - den mockar redan bort hela `WineService`,
  oavsett var logiken bor. Konsekvensen: `WineController` tolkar bara
  råa queryparametrar till typade värden (`Sorteringsfält`/
  `SorteringsRiktning`, bundna direkt via Spring, samma mönster som
  `WineType` redan binds i formulären) - `WineService.sök(...)` gör
  själva jobbet.
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
  (`R20` bäst...`R6` sämst), så `ordinal()` ger lägst tal för det bästa
  betyget. "Stigande sortering" ska betyda stigande betygsVÄRDE
  (sämst→bäst, dvs. `R6` före `R20`) - vilket är samma sak som
  **fallande** ordinal, därav `Comparator.comparing(Rating::ordinal).
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
  inom en facett (tomt set = ingen begränsning för den facetten). Land/
  region/underregion-trädet för filterpanelens kryssrutor (`HärkomstNod`,
  `WineService.härkomstträd()`) härleds fräscht från samtliga viner vid
  varje anrop, **alltid obegränsat av aktivt filter** (statiska facetter,
  godkänt val i mockupomgången) - ingen uppslagstabell, matchar samma
  "fri text, normalisera inte i onödan"-linje som `location`/`grapes`.
  Ingen hierarki-medveten filterlogik behövs trots att kryssrutorna visas
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
  engångsskript) är `schema.sql` kopplad till `spring.sql.init.mode:
  always` och körs **automatiskt vid varje appstart**, inklusive i
  produktion. Medveten avvikelse: den här migreringen är ren schema-DDL
  utan datamigrering (Postgres beräknar kolumnvärdet automatiskt, ingen
  befintlig data behöver flyttas/konverteras som oid→bytea-fallet
  krävde), så automatisk/idempotent körning är säker på ett sätt en
  datamigrering inte hade varit. **`spring.jpa.defer-datasource-
  initialization: true` krävs** för att `schema.sql` ska köras EFTER
  Hibernates `ddl-auto: update` skapat `wines`-tabellen, inte innan
  (annars kraschar `ALTER TABLE` mot en tabell som inte finns än).
  Bekräftat fungera mot en riktig, helt ny Postgres via
  `WineListResponsiveIT` (Testcontainers, `@SpringBootTest`).
  `WineRepository.search(String)` implementeras olika i de två
  adaptrarna (`JpaWineRepository` mot riktig `tsvector`/`ts_rank`,
  `InMemoryWineRepository` mot en enkel skiftlägesokänslig
  delsträngsmatchning). Böjningsform-medvetenheten (stemming) verifierad
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
  försumbar för samlingsstorleken.
- **Sökning ignorerar diakritiska tecken (WINE-7, 2026-07-24)** - en
  sökning på "albarino" hittar nu druvan "Albariño". Två separata
  fixar, en per `WineRepository`-adapter (se ADR 0007):
  - `JpaWineRepository`: ny textsökkonfiguration `swedish_unaccent` i
    `schema.sql` (kopia av `'swedish'` med `unaccent`-ordboken kedjad
    före `swedish_stem`), använd i både `search_vector`s uttryck och
    `plainto_tsquery(...)`-anropen. **Fälla undviken:** en vanlig
    `unaccent(text)`-funktion hade INTE fungerat direkt i
    `GENERATED ALWAYS AS`-uttrycket - Postgres kräver `IMMUTABLE` där,
    och `unaccent()` är bara `STABLE`. En namngiven textsökkonfiguration
    (`to_tsvector('swedish_unaccent', ...)`) räknas däremot som
    `IMMUTABLE` oavsett vilka ordböcker den kedjar internt. Verifierat
    manuellt mot en fristående Postgres-container: en rad med
    `grapes = 'Albariño'` matchades faktiskt av
    `plainto_tsquery('swedish_unaccent', 'albarino')`, en rad med
    `grapes = 'Nebbiolo'` gjorde det inte.
  - `InMemoryWineRepository`: normaliserar bort diakritiska tecken med
    `java.text.Normalizer` (NFD-normalisering + ta bort Unicode-
    kategorin `\p{M}`, kombinerande tecken) på både sökterm och
    fältvärden, utöver den befintliga skiftlägesnormaliseringen. Till
    skillnad från böjningsform-medvetenhet (en genuint Postgres-
    specifik nyans) är diakritikborttagning billig och exakt att
    återskapa i Java, så här finns ingen anledning att låta adaptrarna
    bete sig olika.
- **Chips (byggt 2026-07-22) - vanliga `<a href>`, inte htmx.** En chip
  per aktivt filter-/sökvärde, med en borttagningslänk
  (`WineController.Sökvy.urlUtan(facett, värde)`, `UriComponentsBuilder`)
  som bygger om hela URL:en minus det enskilda värdet. Medvetet INTE
  htmx-drivet: en borttagning måste uppdatera hela verktygsraden
  (kryssrutor, sökfält), inte bara `#vinlista`-fragmentet en htmx-swap
  annars hade varit begränsad till.
- **`DELETE /wines/{id}` (`taBortVin`) kraschade när `chips`/
  `antalTotalt` blev ovillkorliga referenser i `#vinlista`-fragmentet
  (fångat av testsviten, inte manuellt).** Fixat genom att sätta båda i
  `taBortVin` också.
  **Begränsningen (borttagning återställde till standardvyn) fixad
  2026-07-22, på användarens begäran.** `WineController.
  fyllIVinlistaModell(...)` delas nu av både `GET /` och
  `DELETE /wines/{id}`. Löst utan `hx-include`: "Ta bort"-knapparnas
  `hx-delete`-URL byggs nu med `@{...}`-länkuttrycket och alla sju
  queryparametrarna direkt i `vinkallare.html`. Verifierat med
  Playwright mot en riktig körande app: ta bort ett rött vin medan
  "Rött"-filtret är aktivt lämnar filtret aktivt och det kvarvarande
  röda vinet synligt efteråt.
- **`Wine` har 23 fält** (växte från ursprungliga sju via Excel-importen)
  - en positionell record-konstruktor med den längden vore oläsbar.
  Använd `Wine.builder()...build()` (och `vin.toBuilder()...build()` för
  with-metoder) på alla anropsplatser. Motsvarande i `WineEntity`:
  no-arg-konstruktor + paketprivata settrar. **Status:** alla fält är
  redigerbara i webb-UI:t via `vin-formular.html` - en egen sida, inte
  ett htmx-fragment i listan, eftersom 23 fält i en radform vore
  ohanterligt. **Samma mall och i praktiken samma sida används för
  både tillägg och redigering** (`GET /wines/nytt` respektive
  `GET /wines/{id}/redigera`). `POST /wines` (tillägg) och
  `POST /wines/{id}/redigera` delar en privat
  `tillämpaFormulärfält(...)`-metod i `WineController`.
  Kontrollermetoderna tar emot alla valfria fält som rå `String` och
  tolkar dem själva (blankt fält → `null`) istället för att låta Spring
  binda direkt till `Rating`/`LocalDate`/`BigDecimal`. Formuläret är
  `multipart/form-data` och tar emot en valfri `MultipartFile bild` i
  samma anrop - `medBildOmVald(...)` sätter bara `image`/
  `imageMimeType` om en fil faktiskt valdes.
  **Vinlistan visar alla icke-tekniska fält (byggt 2026-07-19, fältfördelningen
  justerad samma dag efter användarfeedback):** översikten i tabell/kort
  visar bild, namn, typ, producent, land, region, underregion, druvor,
  årgång, flaskor, eget betyg, Munskänkarnas betyg och Vivino-betyg -
  geografi- och betygsfälten flyttades hit från detaljvyn eftersom
  användaren vill se dem utan att fälla ut något extra. Resterande fält
  är infällda under en `<details>`-baserad "Detaljer"-sektion per
  rad/kort. Ett delat Thymeleaf-fragment
  (`th:fragment="detaljfalt(vin)"` i `vinkallare.html`) återanvänds av
  både tabell- och kortvyn.
  **Fälla:** `th:fragment` döljer inte elementet från normal
  toppnedrendering av sidan - fragmentet ligger som ett syskon till
  `th:fragment="lista"`-diven, utanför alla `th:each`, så utan en extra
  vaktklausul (`th:if="${vin != null}"` på fragmentets rotelement)
  kraschar helsideslaster (`GET /`) med `EL1007E: Property or field
  'region' cannot be found on null`.
  **Tabellvyns detaljrad fick egen `<tr>` (fixat 2026-07-19):** den
  ursprungliga varianten la `<details>` i tabellradens sista `<td>`, så
  det uppfällda innehållet klämdes in i den smala kolumnens bredd även
  på en stor skärm. Fixat genom att låta varje vin rendera **två**
  `<tr>` (huvudrad + `<tr class="detaljrad">` med en enda `<td
  colspan="14">`), grupperade med `<th:block th:each="vin :
  ${viner}">`.
  **Kortmallen designad om efter en PNG-mockup (2026-07-19), avgränsad
  till kortvyn.** `.vinkort` gick från ett vertikalt stack med
  `<dl>`-fältetiketter till en tvådelad layout: `.vinkort-topp` är en
  flex-rad med en smal bildkolumn (`.vinkort-bildyta`, `flex: 0 0
  5.5rem`) och en textkolumn, medan betygen, `<details>` och
  `.vinkort-fot` ligger som egna block direkt under `.vinkort`. Betygen
  fick etiketten *ovanför* värdet (`.betyg-label`/`.betyg-varde`) efter
  att en första version med etikett/värde på samma rad gav radbrytning
  mitt i långa betygstexter. Antal flaskor löstes som en badge i
  kortets övre högra hörn (`.flaskor-badge`).
  **Redigera/Ta bort flyttade in i Detaljer, högerjusterade (2026-07-19,
  gäller både tabell- och kortvyn).** Ligger nu sist i den infällda
  `<details>`-sektionen i en delad `.detalj-atgarder`-`<div>`. Eftersom
  hela åtgärdskolumnen försvann ur tabellens `<thead>` och huvudrad
  sänktes `colspan` på detaljraden från `14` till `13`.
  **Fälla att komma ihåg för nästa kolumnändring:** `colspan` måste
  alltid matcha exakt antalet `<th>` i `<thead>`, annars blir
  detaljradens `<td>` fel bred - lätt att missa eftersom det inte ger
  något kompilatorfel, bara ett tyst layoutproblem som bara syns
  visuellt.
  **Detaljer-fältens ordning omarbetad, scopead till bara kortvyn
  (2026-07-19).** Ny ordning: Inköpsdatum, Pris, Plats, Varför köpt,
  Tasting notes, Systembolagets beskrivning, Munskänkarnas bedömning,
  Annan referens. De fyra sista visar värdet under etiketten istället
  för bredvid. Medvetet **inte** löst genom att ändra `detaljfalt`-
  fragmentets DOM-ordning - varje `dt`/`dd`-par fick en `fd-*`-klass,
  och CSS `order` sätts på dessa klasser **scopeat under `.vinkort
  dl`**. Tabellvyns `.detaljlista-bred` har ingen matchande
  `order`-regel och behåller därför sin egen dokumentordning.
  **Systembolagets produktnummer slogs ihop med beskrivningsraden
  (2026-07-19), i BÅDA vyerna.** `fd-sb-beskrivning`s `dt` bygger nu sin
  text villkorligt: `th:text="${vin.systembolagetProductNumber !=
  null} ? |Systembolagets beskrivning (${vin.systembolagetProductNumber})|
  : 'Systembolagets beskrivning'"`. **Om `systembolagetDescription` är
  `null` visas produktnumret inte alls**, även om det är satt.
  **Tabellvyns designomgång (2026-07-19/20)** - styrd av en PNG-mockup
  och en Artifact-jämförelse som itererades i flera omgångar: dämpade
  labels, betygsraden flyttad upp bredvid bilden, fältordning, labels
  linjerade på samma höjd, fasta betygskolumnbredder. Beslutet var
  uttryckligen **ingen infälld Detaljer på desktop**.
  - `#vinlista-tabell` innehåller nu `.vinkort-bred`-kort, inte en
    `<table>`. `vk-`-prefixet är medvetet skilt från kortvyns
    `vinkort-`-prefix.
  - Fyra kolumner delas av `.vk-topp`/`.vk-info-rad`/`.vk-text-rad` via
    `grid-template-columns: 6rem 1fr 18rem 18rem`. **Varje fält har ett
    explicit `grid-column`** - utan det skulle CSS Grids auto-placering
    fylla nästa lediga cell i dokumentordning, och ett vin utan
    `purchaseDate` skulle få Pris att hoppa in i fel kolumn.
  - Betygsraden är en egen `grid-row: 2` i `.vk-topp`, bredvid bilden
    (`.vk-bildyta`, `grid-row: 1 / 3`).
  - `.vk-munskankarna`/`.vk-egetbetyg` har fast bredd (`18rem`, inte
    `fr`) - måste rymma det längsta möjliga betygsvärdet (~41 tecken)
    oavsett vilket av de två fälten som råkar ha ett långt värde.
  - Sidan höjdes i bredd: `body`s `max-width` från `48rem` till
    `70rem`, brytpunkten mellan bred kortvy och mobil kortvy från
    `640px` till `960px`.
  - **Testkonsekvens:** `WineListResponsiveIT` fick
    `skaVisaRedigeraOchTaBortDirektPåDesktop` (inget klick behövs
    längre) plus `skaVisaAllaFältDirektPåDesktopUtanAttFällaUtNågot`.
    `WineControllerTest` fick `skaRenderaBredaKortMedAllaFältSynliga`.
  **Bildens storlek/position justerad i fyra... egentligen sex omgångar
  (2026-07-20)**, efter att användaren tyckte den var onödigt stor och
  sedan ville ha under- och till sist överkanten linjerad. Kolumnbredden
  (`6rem`) rördes **inte** i någon omgång - bara bilden/platshållarens
  `max-width`/`max-height` justerades.
  1. Första försöket: `max-width: 3.5rem; max-height: 5rem`,
     `grid-row: 1` (`align-self: start`) - lämnade tomrum under bilden
     ner till betygsraden. Fälla undviken i efterhand: att krympa
     `max-width`/`max-height` utan att ändra `grid-row` gör bilden
     mindre men lämnar kolumnen lika hög.
  2. Andra omgången: `max-width: 5.5rem; max-height: 8rem` - matchar
     kortvyns kolumnbredd, fortfarande upplevt som för litet.
  3. Tredje omgången: `grid-row: 1 / 3` igen men `align-self: end` +
     `max-width: 6rem; max-height: 9rem`. Skillnaden mellan `stretch`
     och `end`: `stretch` fyller hela ytan oavsett höjd (för stort),
     `end` positionerar en begränsad bild vid nederkanten (liten, men
     linjerad mot betygsradens underkant).
  4. Fjärde omgången: användaren ville ha BÅDA kanterna linjerade. Går
     bara med `align-self: stretch` tillbaka. `height: 100%` (fortsatt
     `max-width: 6rem`) + `object-fit: contain`. Ny fälla: `<a>`-taggen
     runt `<img>` är `display: inline` som standard och saknar en egen
     resolverbar höjd - fixat med `.vk-bildyta a { display: block;
     height: 100% }`.
  5. Femte omgången: `object-position: center bottom` - en riktig
     flaskbild (till skillnad från "Ingen bild"-platshållaren) centrerar
     sitt innehåll som standard och lämnade tomrum både ovanför och
     under. **Testfälla att komma ihåg:** platshållaren och en riktig
     bild beter sig olika med `object-fit`/`object-position` - verifiera
     alltid mot en faktiskt uppladdad bild, inte bara "Ingen bild".
  6. Sjätte omgången: `position: absolute` - den verkliga boven.
     Återskapades bara med **både** en smal/hög testbild (200×1000)
     **och** ett vin med lite text samtidigt - med lite text blir
     bildens naturliga bildförhållande-höjd den dominerande faktorn,
     och en smal/hög bild kunde tvinga upp hela radens höjd trots
     `height: 100%` (grid-/flex-item har `min-height: auto` som
     standard). Ett försök med `min-height: 0` på `.vk-bildyta` räckte
     INTE. Den robusta lösningen: `position: absolute` (`inset: 0`)
     tar bilden helt ur dokumentflödet - absolutpositionerade element
     kan aldrig bidra till förälderns/gridradens auto-storlek.
     **Lärdom om testmetodik:** verifiera alltid med både en ovanligt
     smal/hög testbild och ett vin med minimal text samtidigt, inte
     bara mot "Ingen bild" eller en enda "typisk" kombination.
  **Kortvyns (mobil) label-stil enhetligad med de breda korten
  (2026-07-20).** `.vinkort-betyg .betyg-label` och `.vinkort dt`/`dd`
  fick samma deklarationer som `.vk-label`/`.vk-value` - kopierade
  deklarationer på egna klasser, inte samma klassnamn återanvänt.

## Säkerhet - historik (superseded av formulärinloggning, se CLAUDE.md)

- **Hela appen krävde HTTP Basic-inloggning** via `SecurityConfig`
  (`.anyRequest().authenticated()` som fallback) - appen hade ingen
  separat publik läsvy, så varje route lät i grunden en besökare ändra
  vinsamlingen, och appen var redan nåbar från nätet innan detta beslut
  togs. ADMIN-kontot hette `admin`, lösenord från
  `winecellar.admin.password`/miljövariabeln
  `WINECELLAR_ADMIN_PASSWORD` (default `admin` bara lokalt).
- **READONLY-kontot (byggt 2026-07-19):** `readonly`/`readonly` - både
  användarnamn och lösenord hårdkodade i `SecurityConfig`, eftersom
  kontot medvetet var tänkt att vara ett känt, delbart
  "titta men inte ändra"-konto. Fick GET `/` och GET `/wines/{id}/bild`
  (`hasAnyRole("ADMIN", "READONLY")`), men nekades allt annat.
  `WineController.vinkällare`/`taBortVin` satte en
  `kanRedigera`-modellattribut som `vinkallare.html` använde för att
  dölja "Lägg till vin"-länken och Redigera/Ta bort för READONLY - bara
  ett extra UI-lager, inte den faktiska åtkomstkontrollen.
- CSRF var avstängt globalt: htmx-formulären skickade ingen
  CSRF-token, och autentiseringen var stateless Basic-auth per anrop -
  inte en inloggad session som CSRF-skyddet är till för.
- **`WINECELLAR_ADMIN_PASSWORD` var satt i Clever Cloud-konsolen och
  verifierad (2026-07-12)**: standardlösenordet `admin`/`admin` gav 401
  mot produktionsappen, ett riktigt lösenord gav 200. Clever Cloud
  läser miljövariabler vid processstart, så en sparad variabel kräver
  en omstart/redeploy för att slå igenom.

Hela HTTP Basic-modellen (inklusive ADMIN/READONLY) är ersatt av
formulärinloggning med session, se ADR 0013/0009 och avsnittet "Flera
användare" nedan.

## Etikettskanning (LLM)

**Byggd 2026-07-24 (WINE-5).** Appens första beroende av en extern
tjänst (Anthropic) utöver Postgres - se
[ADR 0012](adr/0012-label-scanning-llm-integration.md) för
motiveringen bakom de arkitektoniska valen (port/adapter,
`RestClient` istället för den officiella SDK:n, konfiguration via
miljövariabler, testuppdelningen mellan Cucumber/MockMvc/Playwright).
Punkter värda att komma ihåg utöver ADR:n:

- **`LabelInterpreter.interpret(...)` returnerar `Optional<InterpretedLabel>`,
  inte ett värde-objekt med en egen "misslyckades"-flagga.** `empty()` =
  total misslyckning (nätverksfel, LLM-fel, eller alla fem fälten blev
  `null`) - ett `InterpretedLabel` med enstaka `null`-fält är fortfarande
  ett LYCKAT resultat (bara namnet gick t.ex. att läsa).
- **`LabelInterpretationService.interpretedFields()` räknas ut från
  vilka av de fem fälten som är icke-`null` i svaret** - ingen separat
  boolesk flagga per fält behövdes.
- **Etikettskanningens formulärfält döljs helt vid redigering
  (`th:if="${wine.id == null}"` i `vin-formular.html`)** - att skanna om
  ett redan sparat vin är inte en del av WINE-5:s scope.
- **`th:classappend`, inte `th:class`, för `tolkat-falt`-markeringen** -
  `th:class` hade skrivit över hela `class`-attributet.
- **Klientsidans nedskalning (Canvas, före uppladdning) är projektets
  första mer-än-triviala JavaScript** - `DataTransfer`/`File`-tricket
  för att ersätta `<input type="file">`s valda fil efter nedskalning är
  standardmönstret för detta.
- **`LabelScanFormIT` (Playwright) mockar `LabelInterpreter`
  (porten), inte `LabelInterpretationService`** - den riktiga tjänsten
  körs alltså i det testet, till skillnad från `WineControllerTest`
  (`@WebMvcTest`) som mockar `LabelInterpretationService` direkt.
- **Playwrights `setInputFiles(...)` med en riktig, avkodningsbar
  1x1-PNG, inte godtyckliga bytes** - klient-JS:en laddar bilden i ett
  `Image`-element för att läsa dess bredd/höjd inför nedskalningen.

**Statusinfo under skanningen (byggt 2026-07-24, WINE-8).** Två separata
statusmeddelanden, inte ett:
- **"Analyserar etikett..."** sätts synkront i JS direkt när filen
  väljs, INNAN Canvas-nedskalningen eller nätverksanropet ens startar.
  Fungerar utan htmx/fetch eftersom hela sidan navigerar bort när
  formuläret skickas in.
- **"Fyllde i: ..."** byggs server-side i `WineController.
  interpretedFieldLabels(...)` från en FAST fältordning
  (`INTERPRETED_FIELD_ORDER`), inte `interpretedFields`s egen
  iterationsordning (ett `HashSet`) - annars hade meddelandet blivit
  icke-deterministiskt mellan körningar.
- **Testfälla:** `th:text` på ett separat `<span>` inuti `<p>`-taggen
  bryter sönder texten med en tagg mitt i - `containsString(...)`-style
  MockMvc-assertions matchar då INTE. Löst med Thymeleafs
  literalsubstitution på hela `<p>`-elementet.
- **Playwright-testet för "Analyserar etikett..." kräver en konstgjord
  fördröjning i den mockade `LabelInterpreter`** (`Thread.sleep(800)`)
  - annars hinner mock-svaret komma tillbaka och sidan navigera bort
  innan assertionen läser statusraden.

## Flera användare (multi-user) - Fas 1, WINE-9 till WINE-18

**Beslutet skrivet ner som [ADR 0013](adr/0013-multi-user-accounts.md)
INNAN implementationen**, motiverat av att omställningen är stor nog
att sträcka sig över flera separata stories. Kort sammanfattning av
besluten (fullständig motivering i ADR:n): öppen självregistrering,
formulärbaserad inloggning med session (ersätter HTTP Basic helt - CSRF
slås på igen), varje användares vinlista är helt privat, en ny `User`-
entitet plus `owner_id`-kolumn på `wines`, och befintliga
produktionsviner (~30 st) knyts till det första riktiga kontot via en
engångsmigrering. Import/export via webben blev en separat, senare fas.

Stories, länkade med "depends on" i den tänkta byggordningen: WINE-9
(ADR:n) → WINE-10 (datamodell) → WINE-12 (formulärinloggning) →
WINE-11 (registrering) och WINE-13 (scopead vinlista) → WINE-16/WINE-18
(testinfrastruktur) → WINE-14 (dataisolering) → WINE-15 (ta bort
ADMIN/READONLY, sist av säkerhetsskäl) → WINE-17 (produktionsmigrering,
sist).

**WINE-10 byggd (2026-07-24): `User`-entitet + `owner_id`-koppling.** Ny
`User`/`User.UserId` (`domain/`), tunt precis som `Wine`/`WineId`, en
`UserRepository`-port och två adaptrar. `WineEntity` fick en
`@ManyToOne owner`-relation mot `owner_id` (nullable, `ddl-auto: update`
skapade både tabellen och FK-constraintet automatiskt). Medvetet inget
annat kopplat än så här - själva scopingen kom i WINE-13.

**Deploy-fälla upptäckt 2026-07-24/25, kopplad till WINE-10:s
`owner_id`-kolumn.** Produktionsdeployen kraschade med
`org.postgresql.util.PSQLException: cannot alter type of a column used
by a generated column` (`grapes`, blockerad av `search_vector`). Orsak:
`WineEntity.grapes`/`tastingNotes`/`systembolagetDescription`/
`munskankarnaReview` hade haft `@Column(columnDefinition = "text")` ett
tag, men Hibernates `ddl-auto: update` hade aldrig tidigare haft ett
skäl att röra `wines`-tabellen och därför aldrig försökt bredda dem från
`varchar(255)` till `text` förrän `owner_id`-kolumnen gav den ett skäl
att göra en fullständig kolumngenomgång av tabellen - Postgres tillåter
inte den breddningen så länge `search_vector` refererar till kolumnen.
Löst med en engångsmigrering som bara droppar `search_vector` -
`schema.sql` återskapar den automatiskt vid nästa lyckade appstart.
Kördes manuellt mot produktionsdatabasen via en engångs-`docker run
postgres:16 psql ...`-container. **Lärdom:** `ddl-auto: update` kan
dölja den här sortens konflikt i flera deployer på rad om inget annat
ger Hibernate skäl att röra samma tabell.

**Uppföljning (2026-07-25): den första migreringen var inte tillräcklig -
samma "cannot alter type"-fel kom tillbaka på en senare deploy, trots att
en deploy däremellan hade lyckats starta.** Grundorsaken var mer
lömsk än först trott: Hibernates schemamigrering kör flera ALTER-satser
i samma transaktion, i en ordning som styrs av `HashMap`-iteration - inte
garanterat stabil mellan körningar. Om `grapes`-breddningen misslyckas
kan Postgres transaktionsavbrott antingen tystas bort av Hibernates
icke-fatala DDL-felhantering (appen startar ändå, breddningen uteblir
tyst) eller få en SENARE sats i SAMMA transaktion att också fejla på
ett sätt som väl kraschar hela starten. Löst genom att göra breddningen
SJÄLV, direkt i SQL - drop search_vector, `ALTER COLUMN ... TYPE text`
på alla fyra kolumnerna, utan att förlita sig på att Hibernate lyckas
med det vid nästa uppstart. **Lärdom:** lita inte på att en enskild
lyckad deploy bevisar att en `ddl-auto: update`-driven ALTER faktiskt
gick igenom - transaktionsbeteendet vid ett DDL-fel kan dölja att den
tyst uteblev.

**Andra deploy-fällan, samma rotorsak (2026-07-25): sökningen kraschade
i produktion efter att schemat väl gick igenom.** `WineJpaRepository.
search(...)` är en native query med en HÅRDKODAD, explicit kolumnlista
- den listan uppdaterades aldrig när `owner_id` lades till som ett
mappat fält på `WineEntity`, så Hibernate kraschade vid hydrering.
Fixat genom att lägga till `owner_id` sist i kolumnlistan. **Ingen
befintlig automatisk test fångade det här** - täppt igen med ett nytt
scenario, `sokning-mot-postgres.feature`, som kör `WineRepository.
search(...)` mot en riktig Postgres.

**Tredje deploy-fällan, samma symptom igen (2026-07-25) - den slutgiltiga,
strukturella lösningen.** Trots två raka manuella migreringar kom exakt
samma fel tillbaka på nästa deploy (WINE-11, som inte ens rörde
`WineEntity`). Grundorsaken kunde aldrig fastställas med säkerhet -
istället för att fortsätta jaga symptomet togs beslutet att ta bort
själva MÖJLIGHETEN att krascha.
- **Lösning: `search_vector` underhålls nu via en TRIGGER
  (`wines_update_search_vector()` + `wines_search_vector_trigger`),
  inte `GENERATED ALWAYS AS ... STORED`.** En vanlig `tsvector`-kolumn
  har ingen Postgres-begränsning mot att ALTER:a kolumner den beror på.
- **Ny fälla:** Spring Boots `ScriptUtils` delar upp `schema.sql` i
  separata JDBC-anrop genom enkel strängsökning efter `;` - den
  förstår inte PL/pgSQL:s `$$...$$`-citerade funktionskroppar och
  kapade `CREATE FUNCTION ... AS $$ BEGIN ... END; $$` mitt i vid det
  första `;` inuti funktionen. Löst med `spring.sql.init.separator:
  ";;"` i `application.yml`.
- **Verifierat lokalt på det mest realistiska sättet möjligt:** en
  lokal databas fick manuellt återskapa exakt produktionens trasiga
  tillstånd, appen startades om mot det - `\d wines` bekräftade
  beteendet var opålitligt/svårförutsägbart snarare än en konsekvent
  bugg att fixa vid källan. `db/migrations/2026-07-25-search-vector-
  trigger-instead-of-generated.sql` kördes sedan mot produktionsdatabasen
  FÖRE deployen. **Lärdom om testmetodik:** de två tidigare migreringarna
  verifierades bara genom att pusha och se om produktionen råkade
  fungera - den här gången återskapades det FAKTISKA trasiga tillståndet
  lokalt först, vilket är vad som borde ha gjorts från början.

**WINE-12 byggd (2026-07-24): formulärinloggning ersätter HTTP Basic,
med en viktig avvikelse från ursprungsplanen.** `SecurityConfig` bytte
`.httpBasic(...)` mot `.formLogin(...).loginPage("/login").permitAll()`
+ `.logout(...)`. CSRF slogs på igen: `thymeleaf-extras-springsecurity6`
injicerar automatiskt CSRF-fältet i varje `th:action`-formulär, och
`vinkallare.html` fick en `htmx:configRequest`-lyssnare som lägger till
CSRF-headern på htmx-anrop. Ny `LoginController` (`GET /login`) +
`login.html`.
- **Fälla i `<head>`:** `document.body.addEventListener(...)` i ett
  inline-`<script>` i `<head>` kraschar - `document.body` finns inte
  förrän `<body>` har parsats. Löst med `document.addEventListener(...)`.
- **Avsiktlig avvikelse från WINE-12s ursprungliga story-text:**
  `UserDetailsService` var KVAR som den hårdkodade
  `InMemoryUserDetailsManager` (admin/readonly), INTE bytt till att läsa
  från den nya `UserRepository`n - annars hade admin/readonly-
  inloggningen slagits ut direkt (ingen rad i `users`-tabellen ännu).
  Konsekvens: en oautentiserad förfrågan svarade nu 302 till `/login`
  istället för 401.
- **Testfälla, `@MockBean`-läckage:** ett första försök löste
  CSRF-i-tester generellt via en `@TestConfiguration`
  (`MockMvcBuilderCustomizer`) - fungerade isolerat, men fick
  `@MockBean`-mockarna att INTE nollställas mellan tester när HELA
  testklassen kördes i ett svep. Bytt till att lägga `.with(csrf())`
  explicit på varje POST/DELETE/multipart-anrop istället - bevisat
  säkert. Värt att komma ihåg: undvik
  `MockMvcBuilderCustomizer`/`defaultRequest`-mönstret för
  `@WebMvcTest` i det här projektet tills orsaken är förstådd.

**WINE-11 byggd (2026-07-25): öppen självregistrering.** Ny
`GET/POST /registrera` (`RegistrationController` + `registrera.html`),
`RegistrationService` och `RegistrationResult`
(`Registered`/`UsernameTaken`).
- **`SecurityConfig.userDetailsService` slog nu ihop två källor** - de
  gamla hårdkodade `admin`/`readonly`-kontona (kollas först) och
  `UserRepository`n (fallback). Databasen blev inte den enda
  sanningskällan förrän WINE-15.
- **Nyregistrerade användare fick `ROLE_ADMIN` hårdkodat** - en
  medveten temporär förenkling. Praktisk konsekvens just då: alla
  inloggade användare delade i praktiken samma enda vinlista tills
  WINE-13 landade.
- **Auto-inloggning direkt efter registrering** byggdes manuellt
  (`UsernamePasswordAuthenticationToken`s 3-argumentskonstruktor +
  `HttpSessionSecurityContextRepository.saveContext(...)`).
- **Fälla:** `WineControllerTest` slutade kunna bygga sin
  `ApplicationContext` efter `userDetailsService`-beanens nya
  `UserRepository`-beroende - fixat med ett nytt `@MockBean
  UserRepository`. Värt att komma ihåg: varje ny bean-parameter på en
  `@Bean`-metod i `SecurityConfig` måste speglas i ALLA
  `@WebMvcTest`-klasser som `@Import(SecurityConfig.class)`.

**WINE-13 byggd (2026-07-25): vinlistan scopeas per användare.** Den mest
genomgripande storyn i Fas 1. `Wine` fick ett nytt fält, `owner`
(`User.UserId`, nullable), som en vanlig record-komponent. `WineService.
save(Wine)` fick medvetet INGET owner-argument - all ägarlogik sitter i
anropande kod (`WineController`).
- **Alla läsande metoder scopeas, med `null` som "oscopeat" (inte "ägs av
  ingen")**: `WineRepository.findAllByOwner/findByIdAndOwner/
  searchByOwner`. `deleteById` scopeas INTE på repository-nivå -
  `WineService.removeWine` verifierar ägarskap via `findByIdAndOwner`
  FÖRST (no-op annars, inte ett fel).
- **Beslut, avstämt med användaren innan kodning: admin/readonly förblev
  HELT oscopeade fram till WINE-15.** Motiverat av produktionsrisk - att
  kräva ett `UserId` för admin hade antingen låst ute den riktiga
  produktionsanvändaren eller krävt en `users`-rad i förväg.
- **Två separata testfällor hittade under verifiering:**
  1. `WineControllerTest`s globala sök-/ersätt-fix blandade en rå
     parameter med en Mockito-matcher i samma anrop -
     `InvalidUseOfMatchersException`. Fixat med `eq(...)`.
  2. **Allvarligare, hade nästan missats:** `WineEntity.owner` var
     `@ManyToOne(fetch = FetchType.LAZY)`. `open-in-view: false`
     stänger Hibernate-sessionen så fort ett repository-anrop
     returnerar, och `JpaWineRepository.toDomain(...)` läser
     `entity.getOwner().getId()` EFTER det -
     `LazyInitializationException`, kraschade varenda sida som
     listade viner. Upptäcktes INTE av `mvn verify` - bara av en
     manuell, verklig flerkonto-rundtur lokalt. Fixat genom att byta
     till `FetchType.EAGER`. **Lärdom:** `mvn verify`s
     Testcontainers-scenarier bevisar att en enskild sparning/hämtning
     fungerar, inte att en hel lista av flera repository-anrop i följd
     fungerar utanför en enda transaktion.
- **Verifierat manuellt, end-to-end, mot en riktig lokal Postgres innan
  push:** två konton (alice, bob), var och en ser bara sitt eget vin,
  den andras vin ger 404, ett borttagningsförsök mot någon annans vin
  är ofarligt, admin ser båda vinerna.

**WINE-18 (2026-07-25): uppfylld utan kod.** WINE-13:s null-betyder-
oscopeat-design gjorde att alla befintliga Cucumber-stegklasser förblev
opåverkade.

**WINE-14 byggd (2026-07-25): dataisolering, ett eget automatiskt test
för det som redan verifierats manuellt under WINE-13.** Splittat över
två testlager: listans osynlighet (`flera-anvandare.feature`, ny
`MultiUserSteps`) och direkt URL-åtkomst (ny svit i
`WineControllerTest`, webblagret - "kan inte komma åt ett annat vin via
URL" är i grunden ett HTTP-koncept). Ingen ny produktionskod.

**WINE-17 körd (2026-07-25): befintliga produktionsviner (~30 st) knutna
till kontot "Testus".** En `DO $$ ... $$`-sats som slår upp `Testus`s
`UserId` och sätter `owner_id` på alla rader där det fortfarande var
`NULL`, med en explicit `RAISE EXCEPTION` om användarnamnet inte skulle
hittas. Bekräftat i produktionen.
- **Backupförsöket innan migreringen misslyckades, medvetet övergivet
  av användaren.** `pg_dump` gav `permission denied for table
  pg_database`, och en äldre klient gav `server version mismatch`.
  `psql` drabbades INTE av samma problem. Användaren valde medvetet att
  köra migreringen utan backup.
- **Deltillägget "gör owner_id NOT NULL" flyttades till WINE-15** - kan
  inte göras förrän admin/readonly är borttagna.

**WINE-15 byggd (2026-07-25): ADMIN/READONLY och de hårdkodade kontona
borttagna - sista storyn i Fas 1.** `SecurityConfig` skriven om i
grunden: `UserDetailsService` läser numera bara från `UserRepository`,
`authorizeHttpRequests` är bara `.requestMatchers("/registrera").
permitAll()` + `.anyRequest().authenticated()`. `WINECELLAR_ADMIN_
PASSWORD` borttagen ur `application.yml`. `WineController.
hasAdminRole(...)` och modellattributet `canEdit` borttagna helt.

- **`owner_id` gjord `NOT NULL` i `schema.sql`**, inte via
  `@JoinColumn(nullable = false)` - samma lärdom om Hibernates
  `ddl-auto: update` som `search_vector`-sagan ovan, fast i motsatt
  riktning (skärpa en begränsning istället för att lätta på en).
- **Testsviten krävde en större omskrivning eftersom den bara kunde
  logga in som de nu borttagna kontona.** `WineControllerTest`s hela
  `ReadonlyKontot`-testklass (7 test) togs bort. `InloggningOchUtloggning`
  bytte till en `@BeforeEach` som stubbar `userRepository.
  findByUsername(...)`. `WineListResponsiveIT`/`LabelScanFormIT` fick
  istället registrera ett riktigt konto via `RegistrationService`.
- **Ny testfälla, hittad av `mvn verify`:** `WineListResponsiveIT` hade
  tidigare TVÅ separata `@BeforeEach`-metoder - slogs ihop till EN,
  eftersom JUnit 5 inte garanterar körordning mellan flera
  `@BeforeEach` på samma klass, och vinets `.owner(testkontoId)` kräver
  att kontot redan är skapat.
- **Den allvarligaste fällan: en klassöverskridande `@Before`-hook-krock
  i Cucumber, som bara `mvn verify` avslöjade - i tre separata
  omgångar.** `PersistenceSteps` sparar viner via `wineService.
  save(...)`, vilket nu kräver en riktig ägare eftersom `owner_id` är
  `NOT NULL`. Men `RegistrationSteps` har en egen `@Before`-hook som gör
  `userRepository.deleteAll()` - och Cucumber-JVM kör **alla**
  `@Before`-hooks från **alla** laddade stegklasser för **varje**
  scenario, inte bara från klasser vars steg faktiskt förekommer i
  scenariot. Tre omgångar krävdes för att hitta rätt ordning:
  1. Första försöket: `PersistenceSteps` registrerade ett testkonto
     direkt i sin befintliga `@Before`, utan explicit ordning mot
     `RegistrationSteps`. Kraschade med en FK-överträdelse mot `users`.
  2. Andra försöket: `PersistenceSteps` fick `@Before(order = 1)` och
     `RegistrationSteps` `@Before(order = 0)`. Kraschade istället med
     en ANNAN FK-överträdelse, på `users` från `wines`-sidan.
  3. **Lösningen:** dela upp `PersistenceSteps`s enda `@Before`-metod i
     TVÅ - `raderaAllaViner()` (`order = -1`) och
     `registreraTestkonto()` (`order = 1`) - med `RegistrationSteps.
     reset()` (`order = 0`) i mitten. Den tredelade ordningen (viner →
     users → nytt testkonto) är den enda som är FK-säker i båda
     riktningarna. **Lärdom:** när två stegklassers globala
     `@Before`-hooks rör samma tabeller i motsatta riktningar, räcker
     det inte att bara tvinga EN inbördes ordning mellan de två
     metoderna - en delad resurs som både måste tömmas OCH fyllas på i
     rätt ordning kan kräva att en av metoderna delas upp så att
     delarna interfolieras med den andra klassens hook.
- **Verifierat lokalt end-to-end mot en riktig, färsk lokal Postgres
  innan push:** `admin`/`admin` och `readonly`/`readonly` ger båda
  `/login?error`, ett nytt konto kan registreras och loggas in
  automatiskt, ett andra registrerat konto varken ser det första
  kontots vin i listan eller kommer åt det via direkt URL (404).

Detta avslutade Fas 1 (WINE-9 till WINE-18) - appen stödjer flera
oberoende användare, var och en med sin egen, helt privata vinsamling.

## Excel-import (ursprungligt CLI-verktyg, senare ersatt - se Fas 2 nedan)

`tools/import-excel/` var ursprungligen ett **fristående**
engångsprogram (Apache POI), inte en del av den körande applikationen -
egen `pom.xml`, inte ett `<module>` av rot-pom.xml. Modulen är nu
borttagen (se Fas 2, WINE-20) - det här avsnittet beskriver hur den en
gång fungerade.

**Status: byggt och verifierat lokalt (2026-07-17).** Berodde på
`com.example:winecellar` (rotens artefakt) för att återanvända
`Wine`/`WineType`/`Rating`. Krävde en ändring i rotens `pom.xml`:
`spring-boot-maven-plugin` fick `<classifier>exec</classifier>` (senare
borttagen i WINE-20) - utan den skriver `repackage` (bunden till
`package`-fasen) över den vanliga jaren med en Boot-fatjar, vilket gör
den oanvändbar som ett vanligt Maven-beroende.

Skrev direkt via JDBC mot `wines`-tabellen, inte via `WineService`/HTTP.
Bild-kolumnen i själva Excel-filen (Excels "bild i cell", inbäddad rich
data) importerades aldrig - se `VinradParser`/`ImportExcel`.

**Etikettimport från en bildmapp (byggt 2026-07-19, miljövariabeln döpt
om till `WINECELLAR_LOCAL_IMAGE_FOLDER` 2026-07-22 när ExportExcel
började skriva till samma mapp).** `Bildmatchare` matchade filer i en
mapp mot varje vins `name`-fält, exakt filnamnsmatchning. `ImportExcel.
main` kopplade bilden på varje `Wine` via `withImage(...)` **innan**
insert. Två tvetydighetsfall hanterades explicit med utskrivna
varningar: samma filnamnsstam med flera ändelser (hoppas över) och
flera viner med exakt samma namn i Excel-filen (samma bild kopplas till
alla).

**Systembolagets produktnummer fick en egen Excel-kolumn (2026-07-20).**
Källfilen hade tidigare produktnumret hopklistrat som första raden i
samma cell som beskrivningen, delat på den första radbrytningen.
Användaren lade till en ny kolumn direkt efter "Eget betyg", vilket
flyttade alla kolumner efter den ett steg åt höger.

**Exportskript tillagt, samma modul (byggt och verifierat 2026-07-22).**
`ExportExcel` läste `wines`-tabellen och skrev en `.xlsx` i exakt samma
kolumnlayout som `VinradParser` förväntade sig. `VinradSkrivare` gjorde
radskrivningen och delade `VinradParser`s `COL_*`-konstanter.
`Databaskoppling` extraherades ur `ImportExcel` för anslutnings-
uppslagning.
- **Rundtursbegränsningen (VinradParser krävde vintyp/land/producent/namn
  vid återimport, trots att webb-UI:t bara kräver namn) löstes samma
  dag, på användarens uttryckliga begäran om en fullständig rundtripp.**
- **Fälla som dök upp vid den manuella verifieringen:** `pom.xml`s
  `exec-maven-plugin` hade `<mainClass>` hårdkodat direkt till
  `ImportExcel` (inte via en `${...}`-property) - `-Dexec.mainClass=...
  ExportExcel` gjorde alltså ingenting. Fixat med en `exec.mainClass`-
  property.

**Bildexport tillagd (byggt 2026-07-22), sedan utökad till en
fullständig rundtripp samma dag.** Första omgången: `VinradSkrivare.
bild(...)` ankrade varje vins `image` som en vanlig POI-`Picture`.
**MIME-typstöd:** JPEG/PNG/GIF - inte WEBP (inget OOXML-bildformat
finns för det, hoppas över med varning).

**Andra omgången (samma dag): full rundtripp, tre samverkande
ändringar.** 1) `VinradParser` lättades till samma regel som webb-UI:t.
2) `Bildmatchare.ÄNDELSE_PER_MIME` lades till. 3) `ExportExcel.
skrivBildfiler(...)` skrev varje vins bild som en riktig fil i
`WINECELLAR_LOCAL_IMAGE_FOLDER`, döpt exakt som vinets namn.

**Fälla som dök upp under den manuella rundtrippsverifieringen:**
`ImportExcel.bindParametrar` band tidigare `wine_type`/`producer`/
`country` direkt utan null-koll, eftersom `VinradParser` tidigare
GARANTERADE att de aldrig var `null`. Så fort `VinradParser` lättades
kraschade en återimport av ett namn-bara vin med
`NullPointerException` istället för att spara `null`. **Lärdom:** en
ändring i en delad parser/valideringsregel måste spåras till ALLA
anropsplatser som förlitat sig på den gamla garantin.

**Körd mot produktionsdatabasen (2026-07-17), 30 viner sparade utan fel.**
Klever Cloud har inget CLI/konsol att köra verktyget *på* - det kördes
lokalt och pratade med Postgres-tillägget över nätverket. Ingen
dedupliceringslogik i verktyget - kör inte en gång till mot samma
databas.

## Fas 2 (import/export via webben) - WINE-19 till WINE-35

**Beslutet är skrivet ner som [ADR 0014](adr/0014-web-based-excel-import-export.md)
innan implementationen** - avstämt med användaren i en diskussion
(retirera CLI-verktyget helt, återanvänd WINE-6:s dubblettidentitet,
torrkörning/förhandsgranskning före commit, EN gemensam dubblettstrategi
per import, bildnamngivning `<producent>_<namn>_<årgång>` med fallback,
export som xlsx+zip). Stories WINE-19 (ADR) till WINE-26, länkade med
"depends on": WINE-19 → WINE-20 (flytta parser/writer in i huvudappen,
ta bort CLI-modulen) → WINE-21 (bildnamngivning) → WINE-22
(webbexport xlsx)/WINE-24 (webbimport torrkörning) → WINE-23
(bildexport zip)/WINE-25 (webbimport commit) → WINE-26
(acceptanstest/Playwright).

**WINE-20 byggd (2026-07-25): `tools/import-excel/` (hela modulen,
inklusive `pom.xml`, `ImportExcel`, `ExportExcel`, `DatabaseConnection`)
är borttagen.** `WineRowParser`, `WineRowWriter`, `ImageMatcher` (och
deras enhetstester) flyttades **oförändrade i beteende** till
`infrastructure/excel/` i huvudappen - bara paketnamnet ändrades, plus
att klasserna gick från paketprivata till `public`.
- **`ImageMatcher`s matchning var fortfarande namn-bara** i den här
  storyn - bytet till `<producent>_<namn>_<årgång>` med fallback var
  medvetet WINE-21:s jobb.
- **`DatabaseConnection`, `ImportExcel`, `ExportExcel` flyttades INTE**
  - de var CLI-specifika och ersattes av riktiga webbcontrollerroutes i
  WINE-22 till WINE-25, som skriver via `WineService.save(...)`.
- **Apache POI (`poi-ooxml`) lades till som ett vanligt beroende i
  rotens `pom.xml`** - första gången POI blir ett riktigt
  runtime-beroende av den deployade jaren.
- **`<classifier>exec</classifier>` togs bort** - fanns bara för att
  `tools/import-excel` skulle kunna bero på huvudartefakten som ett
  vanligt Maven-bibliotek. Verifierat efteråt: `mvn package` producerar
  återigen en enda `winecellar-0.1.0-SNAPSHOT.jar`, fortfarande en
  riktig Spring Boot-fatjar.
- **`ADR 0010` markerad Superseded av `ADR 0014`** i den här storyn.
- Verifierat: `mvn verify` grön, plus en manuell `mvn package`-kontroll
  av jar-strukturen.

**WINE-21 byggd (2026-07-25): bildnamngivning `<producent>_<namn>_<årgång>`
med fallback.** `ImageMatcher.findImage(...)` bytte signatur från
`findImage(String wineName)` till `findImage(String producer, String
name, Integer vintage)` - försökte i första hand slå upp den
fullständiga identitetsstammen om BÅDE `producer` och `vintage` var
satta, och föll då ALLTID tillbaka till namn-bara uppslagning om det
första försöket inte gav träff. **Denna bredare fallback-logik ändrades
senare i WINE-35, se nedan - fallbacken är INTE längre ovillkorlig.**
- **`identityFileNameStem(producer, name, vintage)`** var en ny `public
  static`-metod på `ImageMatcher`, tänkt att återanvändas av WINE-23.
  Togs senare bort som dödkod i WINE-30 (se nedan).
- **Mellanslag i producent/namn ersattes med understreck** i den
  beräknade stammen - upptäckt under testskrivningen: ett första
  testfall skrev filen som `Pio_Cesare_Barolo_2018.jpg` men
  `identityFileNameStem` hade byggt `"Pio Cesare_Barolo_2018"`
  (bokstavligt mellanslag kvar). Löst med en `withoutSpaces(...)`-
  hjälpare. **Denna regel ändrades senare i WINE-30 - mellanslag bevaras
  nu, se nedan.**
- **`WineRowWriter` rördes INTE** i den här storyn - den skrev bara
  ankrade xlsx-bilder, inte separata bildfiler.

**WINE-22 byggd (2026-07-25): webbaserad export av vinlistan (xlsx).**
Ny `GET /export/xlsx` (`ExportController`) - skriver bara den inloggade
användarens egna viner (`WineService.listWines(owner)`), sorterade på
namn, via `WineRowWriter`.
- **`CurrentUser` extraherad ur `WineController.currentOwner(...)`** -
  andra verkliga anropsplatsen gjorde det värt det.
- **`WineRowWriter` fick `SHEET_NAME`/`writeHeaderRow(Sheet)`** -
  flyttat hit från den borttagna CLI-klassen `ExportExcel`.
- **`ExportControllerTest`** öppnar de faktiska svarsbytes:en med POI:s
  `WorkbookFactory` och läser tillbaka celler, eftersom svaret är
  binärt (xlsx).
- Verifierat manuellt mot en riktig lokal Postgres, `mvn verify` grön.

**WINE-23 byggd (2026-07-25): bildexport som zip-nedladdning.** Ny
`GET /export/bilder.zip` (samma `ExportController`) - en fil per vin
med sparad bild hos den inloggade användaren, byggd i farten med
`java.util.zip.ZipOutputStream`.
- **`ImageMatcher.EXTENSION_BY_MIME` breddad till `public`.**
- **Ny `ImageMatcher.fileNameStem(producer, name, vintage)`** -
  skrivsidans motsvarighet till läsningens `findImage`-fallback.
- **En namnkrock hittad under designarbetet:** två viner med exakt
  samma namn och utan fullständig identitet skulle råka få samma
  beräknade filnamnsstam. `ZipOutputStream.putNextEntry(...)` tillåter
  INTE två poster med samma namn. Löst genom att hoppa över en
  krockande bilds post nummer två och framåt (`Set<String>` håller
  reda på redan använda postnamn). Ingen varning visas för användaren.
- Verifierat manuellt: `unzip -l` visade exakt en fil, namngiven
  `Pio_Cesare_Barolo_2018.png`, byte-identisk med originaluppladdningen.

**WINE-24 byggd (2026-07-25): webbaserad import - torrkörning/
förhandsgranskning, INGET sparas.** Ny `GET/POST /import`
(`ImportController`) och `application.ImportPreviewService`
(kategoriserar redan tolkade `Wine`-kandidater mot `WineService.
checkForDuplicate`). Fem sammanfattningstal (rader totalt, hoppade-över,
fullständiga/partiella dubbletter, rena nya).

**En design-diskussion innan kodningen ändrade lösningen väsentligt.**
Ursprungsplanen (lagra uppladdade bilder rakt i HTTP-sessionen mellan
torrkörning och commit) ifrågasattes av användaren: "kommer det att
fungera med 100 bilder som kanske inte är särskilt komprimerade?". Två
verkliga problem identifierade innan någon kod skrevs:
1. `application.yml`s multipart-gräns var redan `max-request-size: 5MB`
   - en enda bulk-request med ~100 okomprimerade telefonfoton hade
   avvisats direkt.
2. HTTP-sessionen är Tomcats vanliga in-minnet-lagring - tiotals-
   hundratals MB bilddata per pågående import hade legat kvar i
   JVM-heapen så länge sessionen levde.

**Lösning:**
- **Klientsidans Canvas-nedskalning** (`import.html`) - utökad till att
  hantera POTENTIELLT MÅNGA filer via `Promise.all(...)`, med
  skicka-knappen inaktiverad och ett statusmeddelande medan
  nedskalningen pågår.
- **`max-file-size`/`max-request-size` höjda till 10MB/50MB.**
- **Uppladdad xlsx + bildmapp skrivs till en temporär mapp PÅ DISK**
  (`Files.createTempDirectory("winecellar-import-")`) - bara
  mappsökvägen hålls i `HttpSession`.
- **WINE-27 skapad och länkad** - städning av övergivna temp-mappar
  medvetet UPPSKJUTEN, egen story.
- **Upptäckt under den manuella verifieringen:** `ImportControllerTest`
  skapar riktiga temp-mappar på disk vid varje testkörning - bekräftar
  att WINE-27 är ett verkligt, nära förestående behov.
- **Varje radfel räknas som "hoppas över" via ett brett `catch
  (RuntimeException e)`** runt varje rads parsning - medvetet bredare
  än storyns bokstavliga "saknar namn"-formulering.
- Verifierat: `mvn verify` grön, plus en manuell end-to-end-rundtur.

**WINE-25 byggd (2026-07-26): webbaserad import - commit-steget som
faktiskt sparar.** Ny `POST /import/commit`. Läser tillbaka den
torrkörda filen/bildmappen från temp-mappen, tillämpar den valda
dubblettstrategin per rad, sparar via `WineService.save(...)`/
`increaseQuantity(...)`, städar bort temp-mappen efteråt.
- **Post-Redirect-Get med flash-attribut, inte en direkt rendering av
  resultatet** - annars hade en siduppdatering (F5) orsakat en NY,
  dubblerande import-körning.
- **`parseRows` refaktorerad till att ta emot en `InputStream` direkt**
  - delas nu mellan torrkörningen och commit-steget.
- **Två nya enum:er, `FullDuplicateStrategy`/`PartialDuplicateStrategy`**
  (paketprivata nästlade enum:er i `ImportController`).
- **Testfälla hittad av `mvn test`:** två nya MockMvc-tester antog fel
  att `wineService.save(...)` skulle anropas NOLL respektive EN gång -
  testfilens fjärde rad var medvetet en ren, icke-dubblett rad i ALLA
  scenarier och sparades alltså ALLTID.
- **`ImportControllerTest`s dubblettmockning fick riktiga
  `Wine`-fixturer MED `id`** istället för att eka tillbaka
  kandidatvinet självt som "existing".
- Verifierat manuellt, end-to-end: exporterade, laddade upp samma fil,
  valde "öka antal" för fullständiga dubbletter, committade - resultatet
  var korrekt, flaskantalet hade verkligen ökat i databasen.

**WINE-26 byggd (2026-07-26): Playwright-täckning för hela import-/
exportflödet - och en riktig bugg hittad av just det.** Ny
`ImportExportFlowIT`: konto A lägger till ett vin med bild, exporterar
både `.xlsx` och bildzip, konto B laddar upp samma filer via `/import`,
kör torrkörningen, bekräftar, och kontot B:s vinlista verifieras
innehålla vinet med en fungerande bild efteråt.
- **Ingen Cucumber-scenario byggdes för de återstående
  dubblettstrategikombinationerna** - den faktiska logiken bor i
  `ImportController`, ingen application-lagers-tjänst att skriva ett
  Cucumber-scenario MOT. Lades istället till som tre nya `@Test`-
  metoder i `ImportControllerTest`.
- **Playwrights `setInputFiles` på en `webkitdirectory`-input KRÄVER en
  mappsökväg, inte en lista med enskilda filsökvägar.** Löst genom att
  packa upp zip-filen till en EGEN temporär mapp.
- **Riktig produktionsbugg hittad: klientsidans Canvas-nedskalning
  skrev alltid om bildinnehållet till JPEG, men behöll bildens
  URSPRUNGLIGA filnamn (inklusive dess ursprungliga ändelse, t.ex.
  `.png`).** `ImageMatcher.findImage(...)` bestämmer MIME-typ utifrån
  filens ÄNDELSE, inte dess faktiska innehåll - en fil som fortfarande
  hette `....png` men vars bytes nu var JPEG fick alltså MIME-typen
  `image/png` felaktigt rapporterad. **Ingen tidigare verifiering kunde
  ha hittat den här buggen** - bara en RIKTIG webbläsare som faktiskt
  kör den riktiga nedskalnings-JS:en avslöjade missmatchningen. Fixat i
  `import.html`: filnamnets STAM behålls, men ändelsen byts uttryckligen
  till `.jpg`.
- **Testets bildjämförelse är medvetet INTE byte-identisk** - eftersom
  klientsidans nedskalning ALLTID skriver om bilden till en komprimerad
  JPEG, är en förlustfri rundtripp genom `/import` inte ens avsedd.

**[ADR 0015](adr/0015-bulk-import-images-lossy-jpeg.md) skriven
2026-07-26, på användarens initiativ** - fångar formellt beslutet
(redan byggt i WINE-24/verifierat av WINE-26) att bulkimportens bilder
medvetet INTE rundtrippar bit-exakt.

**WINE-29 (2026-07-27): transparens bevaras vid bulkimport - ADR 0015
uppdaterad, inte reverserad.** En bild med transparent bakgrund tappade
sin transparens helt vid bulkimport (rapporterad bugg) - en direkt,
förutsägbar konsekvens av att ADR 0015:s ursprungliga beslut alltid
skrev om till JPEG. Avstämt med användaren innan kodning.
- **`import.html`s Canvas-kod skannar nu alfakanalen** (`getImageData`)
  efter nedskalningen, innan `toBlob` anropas. Helt ogenomskinliga
  bilder är HELT oförändrade - bara bilder med minst en transparent/
  halvtransparent pixel begär `'image/webp'` istället.
- **Filändelsen läses av EFTER `toBlob`, från blobbens FAKTISKA
  `type`** - eftersom en webbläsare som inte kan koda WebP MÅSTE falla
  tillbaka till PNG (aldrig JPEG). Samma klass av bugg som WINE-26 en
  gång fixade skulle annars kunna återuppstå.
- **Verifiering krävde en RIKTIG webbläsare, inte Java-avkodning** -
  JVM:ens `ImageIO` saknar inbyggt WebP-stöd. `ImportExportFlowIT` kör
  istället `Page.evaluate(...)` i webbläsaren själv.
- **Ny testbild krävdes:** den befintliga `EN_PIXEL_PNG`-fixturen visade
  sig vid närmare granskning vara grayscale+alfa men med alfavärde 255
  - triggar inte den nya kodvägen. En ny `HALVTRANSPARENT_PIXEL_PNG`-
  fixtur byggdes istället.

**WINE-32 byggd (2026-07-27): "Bild"-kolumnen i Excel-import/export
borttagen helt - ADR 0011 markerad Deprecated.** Kolumnen lästes redan
aldrig vid import - den enda kvarvarande användningen var att
`WineRowWriter` skrev en ankrad POI-`Picture` dit vid export, en ren
visuell bekvämlighet som aldrig var en del av den faktiska
bildrundtrippen (den går via `/export/bilder.zip`).
- **Kolumnen togs bort helt ur layouten, inte bara tömdes** - layouten
  gick från A-V (22 kolumner) till A-U (21).
- **`WineRowWriter`:** `image(...)`-metoden, `"Bild"` ur `HEADERS`,
  `POI_PICTURE_TYPE_BY_MIME`-kartan och `Drawing<?> drawing`-parametret
  togs bort helt.
- **ADR 0011 markerad Deprecated, med en konkret motivering skriven in
  i ADR:n själv** - båda mekanismerna beslutet en gång beskrev är nu
  borta, och det finns ingen efterträdande ADR.

**WINE-27 byggd (2026-07-28): övergivna temp-importmappar städas nu.**
[ADR 0017](adr/0017-login-triggered-temp-import-cleanup.md) fångar det
arkitektoniska valet: en lyckad inloggning
(`InteractiveAuthenticationSuccessEvent`) triggar ett svep av alla
`winecellar-import-*`-mappar äldre än 2 timmar i OS-temp-katalogen.
- **En andra, mer akut läcka hittades under kodgranskningen:**
  `ImportController.preview()` (torrkörningen) skapade tidigare en
  HELT NY temp-mapp vid varje anrop, även om sessionen redan hade en
  overkommitterad från en tidigare torrkörning. Fixat genom att ta bort
  en eventuell tidigare, ej committerad temp-mapp INNAN en ny skapas.
- **Ny klass `PendingImportCleanup`** (`web`-paketet) - tar emot
  `tempRoot`/`now` injicerat för testbarhet.
- **Testfälla hittad under testskrivningen:** ett första försök satte
  en testmapps ändringstid till 3 timmar bakåt och skrev SEDAN en fil i
  den - vilket i praktiken uppdaterade mappens mtime tillbaka till "nu"
  igen. Fixat genom att skriva filen FÖRST och sätta `FileTime` sist.

**WINE-30 byggd (2026-07-29): bildnamngivning använder partiell
identitet, inte bara fullständig identitet eller namn-ensamt.**
Buggen: `ImageMatcher.findImage` försökte tidigare bara den fullständiga
identitetsstammen när BÅDA `producer` och `vintage` var satta - annars
föll den direkt tillbaka till namn-bara matchning. Två viner med samma
namn men OLIKA partiell identitet förväntade sig då båda samma
namn-bara bildfil.
- **Ny regel (senare skärpt ytterligare i WINE-35):** `fileNameStem`/
  `findImage` byggde stammen av VILKA fält som faktiskt var satta,
  provade den mest specifika stammen vinets satta fält tillåter först,
  och föll tillbaka till namn-bara matchning om det inte gav träff.
- **Kodgranskning (av Claude, på användarens begäran) hittade två saker
  i den första implementationen som fixades i en uppföljande commit:**
  `ImageMatcher.identityFileNameStem` hade blivit dödkod och togs bort.
  README.md:s exportavsnitt beskrev fortfarande den GAMLA regeln och
  uppdaterades.
- **Uppföljningen gick längre än granskningens frågor också:**
  mellanslag inom producent-/vinnamn bevaras nu (tidigare ersattes de
  med understreck) - bara separatorn MELLAN fälten är understreck, t.ex.
  `Château Margaux_Pauillac Rouge_2015`.

**WINE-35 byggd (2026-07-29): bild-fallback till namn-bara matchning
togs bort för rader med känd identitet - upptäckt av användaren efter
att WINE-30 redan var mergad, inte av granskningen.** WINE-30:s
`ImageMatcher.findImage` föll ALLTID tillbaka till namn-bara matchning
om den specifika stammen inte gav träff - oavsett om producent/årgång
faktiskt var kända för raden. Konsekvens: två viner med samma namn men
olika identitet kunde fortfarande råka dela en namn-bara bildfil så
snart bildmappen inte råkade innehålla den mer specifika filen - exakt
den ursprungliga WINE-30-buggen, bara med lägre sannolikhet.
- **Ny regel (nuvarande, gällande):** ingen fallback alls för en rad med
  MINST ett identitetsfält (producent och/eller årgång) satt - hittas
  ingen fil som matchar den specifika stammen exakt kopplas ingen bild.
  En rad helt utan identitet (bara namnet känt) är opåverkad.
- **Fixen förenklade `findImage` dramatiskt** - hela den villkorade
  fallback-grenen blev överflödig och togs bort helt. Metoden är nu bara
  `return findByStem(fileNameStem(producer, name, vintage));`.
- **Tre tester i `ImageMatcherTest` som testade den gamla (nu
  felaktiga) fallback-logiken skrevs om till att förvänta `null`** -
  en riktig fälla att komma ihåg: en bugfix som gör kod ENKLARE kan
  ändå kräva att flera befintliga, tidigare gröna tester medvetet
  vänds till sin motsats.
- **ADR 0014 punkt 5 uppdaterad** - beskrev tidigare fallbacken som
  ovillkorlig, vilket inte längre stämmer.
