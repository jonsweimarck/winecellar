# Kontext för Claude Code

Se @README.md för arkitektur, datamodell och arbetsprocess. Den här
filen beskriver **nuläget** per delsystem (uppdateras på plats när
något ändras) och en samlad lista över kända fällor som är värda att
komma ihåg om man rör samma kod igen. Den fullständiga, story-för-story-
kronologin (hur vi kom hit, inklusive avslutade återvändsgränder och
verifieringssteg för enskilda YouTrack-stories) finns i @docs/devlog.md
- den laddas inte automatiskt i en session, läs den vid behov.

## Dokumentation

Repot har fyra dokumentationslager med olika syften - blanda inte ihop
dem:

- **`README.md`** - nuläget för en människa som vill förstå/köra
  projektet. Ingen historik.
- **`CLAUDE.md`** (den här filen) - konventioner, nuläge per delsystem,
  kända fällor. Laddas automatiskt i varje session.
- **`docs/adr/`** - bindande arkitektur-/designbeslut, en fil per
  beslut. Mall och skrivregler (bl.a. att undvika citerade metod-/
  klassnamn, felmeddelanden och kodsnuttar i löptexten - tillagt
  WINE-36, 2026-08-06, efter att ADR:erna hade driftat mot att bli
  kod-dokumentation) finns i `docs/adr/README.md` - läs den innan du
  skriver en ny ADR eller ändrar en befintlig.
- **`docs/devlog.md`** - fullständig, kronologisk story-för-story-logg,
  utbruten ur den här filen 2026-08-06. Laddas INTE automatiskt - läs
  vid behov.

## Konventioner

- Hexagonal lagerindelning (`domain/`, `application/`, `infrastructure/`,
  `web/`), `record`/`sealed interface` framför ceremoni.
- Gherkin-scenario tillsammans med utvecklaren innan kod skrivs.
- Surefire/Failsafe-uppdelningen: `*Test.java` (enhetstest, Surefire) vs
  `*IT.java` (acceptans-/UI-test, Failsafe, kräver `mvn verify`).
- `@WebMvcTest` + `@MockBean` för webblagret, verifierar faktiskt renderad
  HTML - inte bara `Model`-attribut.
- Domänlagret är tunt - det här är i praktiken CRUD, ingen affärslogik
  att skydda (se [ADR 0001](docs/adr/0001-thin-domain-layer.md)). Bygg
  inte in skyddsmekanismer eller abstraktioner utan ett tydligt,
  konkret domänskäl.
- UI-komplexiteten ligger i responsiviteten, inte i affärslogiken - se
  [ADR 0002](docs/adr/0002-responsive-list-dual-layout.md).
  `WineListResponsiveIT` verifierar faktiskt CSS-/layoutbeteende
  (bred kontra smal vy), inte bara ett htmx-fragments textinnehåll.
- Clever Cloud-deployen: `clevercloud/maven.json` pekar ut
  `spring-boot:run`, PostgreSQL-tillägget måste länkas till den
  specifika appen (länken följer inte med automatiskt om appen skapas
  om), och HikariCP-poolstorleken behöver hållas rimlig mot Clever
  Clouds instansstorlek. Miljövariabler som måste sättas i Clever
  Clouds konsol för att respektive funktion ska vara säker/fungera i
  produktion: `WINECELLAR_ANTHROPIC_API_KEY` (etikettskanning, se
  "Säkerhet - nuläge"), `WINECELLAR_REMEMBER_ME_KEY` (signerar
  "håll mig inloggad"-cookien, se `SecurityConfig` - saknas den är
  remember-me bara avstängt, inte osäkert, men funktionen fungerar då
  inte i produktion. **Sedan WINE-47 loggar `RememberMeKeyGuard`
  (`web`-paketet) en varning vid uppstart** om Clever Cloud-drift känns
  igen (samma `POSTGRESQL_ADDON_HOST`-signal som `ProdProfileGuard`
  redan litar på) men nyckeln ändå saknas/är blank - byggd efter att
  nyckeln visade sig faktiskt saknas i produktion, vilket gjorde "håll
  mig inloggad" helt overksamt utan att något syntes för användaren
  förutom att bli utloggad efter sessionens vanliga timeout. Samma
  "billigt säkerhetsnät, inte en fix i sig"-princip som
  `ProdProfileGuard` nedan - nyckeln sätts fortfarande inte automatiskt)
  och `SPRING_PROFILES_ACTIVE=prod` (aktiverar `application-prod.yml`,
  som tvingar sessionscookien säker - se Kända fällor nedan om varför
  det inte kan vara på som standard; saknas den här variabeln är
  sessionscookien osäker i produktion. **Sedan WINE-43 loggar
  `ProdProfileGuard` (`web`-paketet) en varning vid uppstart** om Clever
  Cloud-drift känns igen (samma signal som datasource-URL:en redan
  litar på, `POSTGRESQL_ADDON_HOST`) men `prod`-profilen ändå inte är
  aktiv - ett billigt säkerhetsnät mot just den här
  fail-insecure-fällan, inte en fix i sig; profilen aktiveras
  fortfarande inte automatiskt).

## Namngivning

- Tabell- och kolumnnamn på engelska, plural för tabellnamn (`wines`).
- **Undantag:** svenska egennamn som syftar på svenska institutioner
  behåller sitt svenska namn rakt av - `munskankarna_review`,
  `munskankarna_rating`, `systembolaget_product_number`,
  `systembolaget_description`. Översätt inte dessa till påhittade engelska
  motsvarigheter ("association_review" etc.) - det är fel typ av
  konsekvens; ett egennamn ska vara igenkännbart, inte översatt.
- **Språket i koden är engelska, rakt igenom - inte bara i domänlagret
  (byggt 2026-07-23, WINE-4).** Gäller `domain/`, `application/`,
  `infrastructure/`, `web/` och testkoden (klassnamn, metodnamn, fält-/
  variabelnamn, enum-konstanter, Thymeleafs modellattributnycklar och
  URL-frågeparametrar som exponerar dem, t.ex. `sok→search`,
  `sortera→sort`, `riktning→direction`, `viner→wines`).
  **Tre undantag:**
  1. Kodkommentarer och Javadoc förblir på svenska (som hela den här
     filen visar).
  2. Cucumber-stegdefinitionernas `@Given`/`@När`/`@Så`/`@Och`-uttryck
     (den Gherkin-matchande textsträngen) och samtliga `.feature`-filer
     förblir på svenska - de är den del av testsviten som avsiktligt
     ska vara läsbar för icke-utvecklare. De underliggande Java-
     metodnamnen för själva stegdefinitionerna lämnas också oförändrade
     (t.ex. `attKällarenInnehållerFöljandeViner`) eftersom de är
     namngivna direkt efter Gherkin-frasen de matchar - men lokala
     variabler/fält/privata hjälpmetoder i samma stegklasser är på
     engelska, liksom själva klassnamnen.
  3. Egennamn som redan var etablerade undantag (Systembolaget,
     Munskänkarna, se ovan) förblir oöversatta.
  CSS-klassnamn, HTML-`id`/`class`-attribut och den svenska UI-texten
  användaren faktiskt ser (etiketter, knapptexter, `<title>` osv.) är
  INTE en del av den här regeln - det är produktens språk mot en
  svensk användare, inte kodens ubiquitous language.
  **OBS:** äldre kod/kommentarer som fortfarande använder svenska namn
  (`Sökkriterier`, `VinradParser`, `.harBild()` osv.) kan förekomma i
  historiska diskussioner - lita på `git log`/den faktiska koden för
  aktuella namn, inte på gamla loggposter i `docs/devlog.md`.

## Domänmodell - nuläge

- **`WineType`** (enum: RED, WHITE, ROSE, SPARKLING, FORTIFIED) och
  **`Rating`** (`domain/Rating.java`, enum med exakt de 29 värdena från
  källfilens `Listor`-flik) är medvetet begränsade till fasta, slutna
  mängder - både som Java-enum och som Hibernate-genererad
  `CHECK`-constraint (`@Enumerated(EnumType.STRING)`). Ingen
  uppslagstabell - 29 fasta strängar är overengineering att normalisera
  bort. `Rating` har korta konstantnamn (`R16`, `R14_5`) som är det
  Postgres faktiskt lagrar, med den fulla svenska etiketten som ett
  separat `label`-fält. `Rating.fraEtikett(text)` normaliserar
  mellanslag innan matchning (källfilen har inkonsekvent dubbla
  mellanslag i några rader).
- **`WineService` har en enda `save`-metod**, inte separata
  `addWine`/`updateWine` - ingen skillnad i validering/sidoeffekter
  mellan att skapa och uppdatera.
- **`name` och `quantity` är de enda obligatoriska fälten** (`name`
  sedan [ADR 0005](docs/adr/0005-only-name-required.md), `quantity`
  återigen obligatoriskt sedan [ADR 0016](docs/adr/0016-quantity-also-mandatory.md),
  som ersätter 0005:s ursprungliga "bara namn"-regel för just det
  fältet). `Wine.quantity` är primitiv `int` (standardvärde 1 i
  webbformuläret, faller tillbaka till 1 server-side om det ändå
  skickas tomt); `vintage` och övriga fält är fortsatt nullable.
  Kontrollermetoderna tar emot fält som rå `String` och tolkar dem
  själva (blankt → `null`) istället för att låta Spring binda direkt
  till `Rating`/`LocalDate`/`BigDecimal`.
- **Filtrering/sökning/sortering orkestreras i `WineService`, inte i
  `WineController`** - se [ADR 0006](docs/adr/0006-search-orchestration-in-application-layer.md).
  Gherkin-scenarierna testar applikationslagret direkt, så
  orkestreringen måste ligga där för att kunna testas utan MockMvc/HTTP.
  `SearchCriteria` (Builder-record) kombinerar facetter (vintyp, land,
  region, underregion) med OCH sinsemellan, ELLER inom en facett.
- **`SortField`-fälla att undvika vid ändring:** varje konstants
  comparator bygger `nullsLast(...)` **efter** att riktningen redan
  avgjort om ordningen ska vara `.reversed()`, inte tvärtom - annars
  hamnar `null`-värden överst vid fallande sortering istället för sist.
  Betygsfälten sorteras via `Rating.ordinal()` (deklarerad i fallande
  betygsordning), inte etikettens bokstavsordning -
  `Comparator.comparing(Rating::ordinal).reversed()` är "stigande" för
  ett betygsfält. Se `docs/devlog.md` för de Gherkin-scenarier som
  ursprungligen avslöjade båda varianterna av felet.
- **Chips är vanliga `<a href>`, inte htmx** - se
  [ADR 0008](docs/adr/0008-filter-chips-plain-links.md). En borttagning
  måste uppdatera hela verktygsraden (kryssrutor, sökfält), inte bara
  vinlistans htmx-fragment.
- **`location`** är fritext, inte en enum - lådor/förvaringsplatser
  förväntas läggas till över tid.
- **`quantity`** är en enkel räknare som ändras direkt vid redigering.
  Inget förbrukningslogg (datum när en flaska dracks) - om det blir
  aktuellt är det en ny, separat tabell, inte en ombyggnad av `wines`.
- **`target/` delas mellan Claude-sessioner som jobbar i samma
  checkout**, oavsett vilken gren var och en står på - ett grenbyte
  rensar bara `src/`, inte kompilerade klasser/resurser. En annan
  sessions `mvn`-körning på sin egen gren kan lämna kvar en stale
  `.feature`-fil (eller annan resurs) i `target/test-classes/` som
  Cucumber sedan plockar upp som om den hörde till DIN gren - ett
  `UndefinedStepException` för ett scenario som inte ens finns i `src/`
  är ett tecken på just det här, inte ett kodfel. `mvn clean verify`
  löser det.
- **Alla IT-klasser delar EN Postgres-container** (`support/
  SharedPostgres`, statiskt startad - inte `@Testcontainers`/`@Container`,
  som ger en container per klass). Den större vinsten är att identiska
  egenskapsvärden låter Spring återanvända testkontexten mellan klasser.
  Konsekvens: databasen delas, så varje test måste sätta upp sina egna
  data i `@BeforeEach` - lita inte på att något överlever mellan
  testmetoder, och notera att `listWines(null)` returnerar SAMTLIGA
  viner (används av en städmetod som alltså tömmer hela tabellen).
- **Playwrights `isVisible()` är fel verktyg för `clip`-dolda element** -
  en 1x1-pixel-input räknas som synlig. Mät den renderade ytan i stället.
  `evaluate` returnerar dessutom `Integer` för hela tal och `Double`
  annars; casta via `Number`.
- **UI-testfälla att komma ihåg:** `WineListResponsiveIT`/
  `ImportExportFlowIT` klickar länkar där de faktiskt sitter i UI:t.
  Flyttas en länk till en annan sida (som export gjorde i WINE-37) går
  testet i timeout, inte ett tydligt "hittade inte elementet" - och
  Surefire-sviten ensam avslöjar det INTE. Kör `mvn verify`, inte bara
  `mvn test`, efter varje ändring av navigationen.
- **Bilder lagras direkt i `wines`-tabellen** (`image` bytea +
  `image_mime_type`) - se [ADR 0004](docs/adr/0004-images-in-bytea.md).
  `image_mime_type` sätts från `MultipartFile.getContentType()` vid
  uppladdning och används oförändrat som `Content-Type` vid visning.
  Vinlistan bäddar aldrig in bilddata i HTML-fragmentet - `<img>` pekar
  mot `GET /wines/{id}/bild`.
- **`Wine` har 23 fält, byggs alltid via `Wine.builder()...build()`**
  (eller `.toBuilder()...build()` för ändringar) - se
  [ADR 0003](docs/adr/0003-wine-builder-pattern.md). Samma mall/sida
  (`vin-formular.html`) för tillägg och redigering; `POST /wines` och
  `POST /wines/{id}/redigera` delar en privat
  `tillämpaFormulärfält(...)`-metod i `WineController`.
- **Vinlistan visar alla icke-tekniska fält direkt** (bild, namn, typ,
  producent, land, region, underregion, druvor, årgång, flaskor, eget
  betyg, Munskänkarnas betyg, Vivino-betyg). Resten (plats, inköpsdatum,
  pris, inköpsanledning, tasting notes, Systembolagets
  produktnummer/beskrivning, Munskänkarnas bedömning, annan referens)
  visas infällt under "Detaljer" **bara på mobil/smal kortvy** - den
  breda desktopvyn (`.vinkort-bred`/`.vk-*`, >960px) visar allt direkt
  utan infällning, se
  [ADR 0002](docs/adr/0002-responsive-list-dual-layout.md).
  Delat Thymeleaf-fragment `th:fragment="detaljfalt(vin)"` i
  `vinkallare.html` återanvänds av båda vyerna - fältordning för
  mobilkortet styrs av CSS `order` på `fd-*`-klasser (scopeat under
  `.vinkort dl`), inte av fragmentets faktiska DOM-ordning; desktopvyns
  `.detaljlista-bred` har ingen sådan regel och behåller
  dokumentordningen. Om Systembolagets beskrivning saknas visas
  produktnumret inte alls, även om det är satt (medveten tradeoff).
- **Redigera-ikonen (WINE-44, ersatte en textlänk) ligger inne i
  "Detaljer" på mobil, men direkt synlig på desktop** -
  `.detalj-atgarder` är samma delade `<div>` i båda fallen, och
  återanvänder `.topprad-ikonlank` (tema.css) för själva ikonlänkens
  utseende trots namnet - en generisk "ikonknapp"-stil, inte något
  topprad-specifikt. **"Ta bort" finns inte längre i vinlistan alls**
  (samma WINE-44) - se `vin-formular.html`s "Farlig zon" nedan.
- **Tabellhuvudets `colspan` på mobilens gamla tabellrad**-mönstret
  finns inte längre (desktopvyn är kort-baserad, inte en `<table>`) -
  se Kända fällor nedan om `.vk-bildyta`s bildjustering om den CSS:en
  någonsin behöver röras igen.

## Designsystem och navigation - nuläge

Se [ADR 0019](docs/adr/0019-shared-theme-css-variables.md).

- **`static/css/tema.css` är den delade stilmallen** - färg, typografi
  och delade komponenter (`.knapp`/`.knapp-sekundar`, `.topprad`,
  formulärkontroller). Sidspecifik layout ligger kvar i respektive mall.
  **Hårdkoda aldrig ett färgvärde i en mall** - då följer den inte med i
  temaväxlingen. Använd variablerna (`--accent`, `--yta-1/2/3`, `--text`,
  `--ram`, `--fel` osv.).
- **Formulär byggs av `.sektionsrubrik` + `.kort` + `.falt`** (alla i
  tema.css), inte `<fieldset>`/`<legend>` - rubriken hamnar då utanför
  ramen, som på inställningssidan. `.faltrad` lägger två korta fält på
  samma rad över 32rem. **Fälten måste vara kortets DIREKTA barn** -
  ett mellanliggande `<form>` bryter `.kort > .falt:first-child` och ger
  en skiljelinje överst i kortet. Lägg `<form>` runt kortet i stället.
- **Filväljare byggs med `.filval`** (tema.css): inputen döljs visuellt
  och en `<label for=...>` fungerar som knapp. Webbläsarens egen
  filknapp går varken att styla eller översätta - texten ("Choose File")
  kommer från webbläsarens språk, inte sidans. Inputen döljs med
  `clip`, INTE `display: none`, så den finns kvar i tabbordningen och i
  formulärinskicket. En `<label class="falt">` kan inte innehålla
  filväljaren (label i label) - använd `<div class="falt">` där.
- **Accenten är reserverad för sidans primära handling.** "Ta bort" är
  medvetet dämpad (ram + felfärgad text), inte en fylld accentknapp -
  den upprepas en gång per vinkort.
- **Mörkt läge** väljs i Inställningar (Auto/Ljust/Mörkt), sparas i
  `localStorage` och sätts av skriptet i `fragments/tema.html`. Det
  skriptet **måste ligga i `<head>` och köras synkront** - annars
  blinkar sidan ljust innan den blir mörk vid varje sidladdning.
- **`/css/**` och `/js/**` är `permitAll` i `SecurityConfig`** -
  login/registrera är anonyma sidor och hade annars renderats helt
  ostylade.
- **Webbläsarens inbyggda valideringstexter är på webbläsarens språk,
  inte sidans** ("Please fill out this field"). `static/js/validering.js`
  ersätter dem via `setCustomValidity` och laddas av de sidor som har
  obligatoriska fält. **Fälla:** ett fält med satt custom-validity
  räknas som ogiltigt tills meddelandet nollställs - glöms det går
  formuläret aldrig att skicka, ens när det är korrekt ifyllt. Filen
  nollställer därför alltid först och sätter meddelandet efteråt.
- **Mallar och `static/`-filer serveras från den BYGGDA kopian** när
  appen körs via `spring-boot:run` (ingen devtools i projektet). En
  ändring i `src/main/resources` syns inte i en redan startad app, hur
  många gånger man än laddar om i webbläsaren - starta om servern.
- **Navigationen:** kugghjul i toppraden → `/installningar`, som samlar
  import (`/import`), export (`/export`) och Logga ut. Import/export
  ligger alltså INTE längre som knappar på vinlistan. `fragments/
  topprad.html` delas av alla inloggade sidor (kugghjul när
  `tillbakaLank` är null, annars tillbaka-pil).
- **`z-index` gör att ett `position: fixed`-element ritas OVANPÅ annat
  innehåll, inte att det undviker det.** Toasten flyttades från botten
  till högst upp/mitten (WINE-37, efter att bottenplaceringen visade sig
  för lätt att missa) - ett första försök med `top: 1rem` gav ett
  uppmätt ~9px 2D-överlapp mot toppradens titeltext ("Vinkällaren"), som
  toasten då hade ritats rakt ovanpå i stället för bredvid. Lösningen är
  att placera den under toppraden (`top: 4.5rem`), inte att finjustera
  mot en specifik textbredd - särskilt eftersom `fragments/topprad.html`
  delas av flera sidor med olika (och på formulärsidan dynamisk) titel.
- **Blanda aldrig `animation` (`@keyframes`) och `transition` på SAMMA
  CSS-egenskap på samma element.** `.toast` gjorde det först för
  `transform` (in-animation via keyframes, uttoning via transition) -
  gav ett kort men mätbart felläge exakt när animationen tog slut och
  elementet föll tillbaka till basregelns värde, särskilt märkbart när
  keyframes använde `translate(-50%, Ypx)` men basregeln `translateX(-50%)`
  (två syntaktiskt olika sätt att uttrycka "samma" transform, som
  webbläsaren inte alltid behandlar som likvärdiga vid övergången).
  Lösning: låt `transform` (centrering) vara en helt konstant basregel
  som varken animation eller transition rör - animera bara `opacity`.
- **Återkoppling efter spara/redigera/ta bort** (`.toast` i tema.css,
  `${feedback}` i vinkallare.html): lägg till/redigera/ta bort (den
  sistnämnda flyttad till en POST+redirect i WINE-44, se nedan)
  redirectar, så meddelandet MÅSTE sättas via `RedirectAttributes.
  addFlashAttribute` (ett vanligt Model-attribut vore borta redan innan
  GET / körs). **`event.detail.target` i en `htmx:afterSwap`-lyssnare är
  INTE en pålitlig referens till det nya innehållet vid
  `hx-swap="outerHTML"`** - target-elementet ersätts ju självt. Sök i
  `document` i stället för att lita på den referensen, annars körs
  aldrig bortfoningen efter en htmx-swap (upptäcktes bara av ett
  Playwright-test som faktiskt väntade ut hela 3-sekunderstimern, inte
  av att toasten syntes - den syntes fint, den försvann bara aldrig).
  Lärdomen gäller fortfarande om ett framtida state-ändrande htmx-anrop
  läggs till, men är sedan WINE-44 inte längre den vägen toasten
  faktiskt visas - se nästa punkt.
- **"Ta bort" flyttades från vinlistan till redigera-sidan i WINE-44**
  ("Farlig zon" i `vin-formular.html`, `POST /wines/{id}/radera`) -
  vinlistans htmx-baserade `DELETE /wines/{id}` (en direkt render av
  `#vinlista`-fragmentet i samma request, ingen redirect) finns inte
  längre. Motiveringen: redigera-sidan är ingen htmx-fragmentkontext, så
  en vanlig POST+redirect med flash-feedback (samma mönster som
  lägg till/redigera) är den naturliga formen där, snarare än att bygga
  ut htmx dit. Bekräftelsen inför raderingen är ett native
  `<dialog>`-element (öppnas/stängs med ett par rader JS,
  `showModal()`/`close()`), medvetet INTE `window.confirm()` - vissa
  webbläsarinställningar blockerar `confirm()` som en popup, vilket hade
  gjort raderingen obrukbar utan tydligt fel i just de lägena. `dialog`/
  `dialog::backdrop`-baststilen ligger i tema.css (`.knapp-farlig` för
  den destruktiva knappstilen, samma dämpade ram-i-stället-för-fylld-yta-
  princip som `.knapp-sekundar`), sidspecifikt innehåll i
  `vin-formular.html`.
- **Vinlistan har två skilda tomma lägen**, styrda av `totalCount`
  (ofiltrerad): tom källare (inbjudan att lägga till/importera, och
  verktygsraden döljs HELT) kontra sökning utan träffar (verktygsraden
  måste vara kvar, annars går filtret inte att ta bort). Verktygsraden
  ligger utanför htmx-fragmentet, så den dyker upp/försvinner först vid
  nästa hela sidladdning - medvetet.
- **Två CSS-fällor som båda kostat tid i vinlistans mobilvy:** (1)
  `.sok-grupp` och `.falt-grupp` sitter på SAMMA element med samma
  specificitet - den sist deklarerade vinner, så ordningen i filen
  avgör; (2) `min-width: 0` måste sättas på själva flex-itemet, inte
  bara på kontrollen inuti - en `<select>` är annars så bred som sitt
  längsta alternativ och tvingar fram radbrytning.
- **"Lägg till vin" är två skilda element**, inte ett omformat: vanlig
  knapp på desktop, flytande rund knapp (FAB) nere till höger på mobil.
  Standardregeln `display: none` för FAB:en måste ligga FÖRE
  media-queryn - samma specificitet gör annars att den sist deklarerade
  regeln vinner och FAB:en förblir dold även på mobil.

## Säkerhet - nuläge

Se [ADR 0013](docs/adr/0013-multi-user-accounts.md) för den fulla
motiveringen. Formulärbaserad inloggning med session (ersätter den
tidigare HTTP Basic-modellen helt, se
[ADR 0009](docs/adr/0009-whole-app-http-basic-auth.md), Superseded).

- Öppen självregistrering på `/registrera` - vem som helst kan skapa
  ett konto. Varje användares vinlista är helt privat (`owner_id` på
  `wines`, `NOT NULL`) - ingen delning, ingen rollindelning. De tidigare
  hårdkodade `admin`/`readonly`-kontona och `WINECELLAR_ADMIN_PASSWORD`
  är helt borttagna.
- `UserDetailsService` läser bara från `UserRepository` (databasen).
  `authorizeHttpRequests` är `.requestMatchers("/registrera").
  permitAll()` + `.anyRequest().authenticated()` - ingen `hasRole`
  någonstans.
- **CSRF är påslaget.** `thymeleaf-extras-springsecurity6` injicerar
  automatiskt CSRF-token i varje `th:action`-formulär; `vinkallare.html`
  har en `htmx:configRequest`-lyssnare som lägger till CSRF-headern på
  htmx-anrop (`hx-delete`), läst från två `<meta>`-taggar i `<head>`.
- **Alla läsande `WineRepository`-metoder scopeas per ägare**
  (`findAllByOwner`, `findByIdAndOwner`, `searchByOwner`). `deleteById`
  scopeas INTE på repository-nivå - `WineService.removeWine` verifierar
  ägarskap via `findByIdAndOwner` FÖRST och är ett no-op (inte ett fel)
  om det inte matchar, samma "bete sig som att vinet inte fanns"-princip
  som gäller överallt annars i appen.
- `WINECELLAR_ANTHROPIC_API_KEY` krävs för etikettskanning - utan den
  startar appen ändå (tom lokal default), men skanningsanropet
  misslyckas. `WINECELLAR_ANTHROPIC_MODEL` är valfri (default
  `claude-sonnet-5`).
- **"Håll mig inloggad" (WINE-40, se ADR 0020)** - en kryssruta i
  `login.html` (postar som `remember-me`, Spring Securitys egen
  defaultparameter). Hash-baserad remember-me (`SecurityConfig`s
  `.rememberMe(...)`), INTE det databasbackade persistenta läget -
  medvetet, se ADR:n för avvägningen. 30 dagars giltighetstid, hårdkodad
  konstant (ingen hemlighet, ingen anledning att göra den
  miljöspecifik). Signeringsnyckeln läses från `winecellar.remember-me.
  key`/`WINECELLAR_REMEMBER_ME_KEY` - samma mönster som
  `WINECELLAR_ANTHROPIC_API_KEY` (ofarlig lokal default, en riktig
  hemlighet krävs i produktion - annars kan cookien förfalskas av vem
  som helst med tillgång till en lösenordshash). En utloggning kan bara
  instruera webbläsaren att KASTA sin egen cookie (`Max-Age: 0`) - det
  hash-baserade läget är helt tillståndslöst server-side, så en tidigare
  kopierad cookie-sträng förblir giltig till sin egen utgångstid
  oavsett; bara ett lösenordsbyte eller en nyckelrotation
  ogiltigförklarar redan utfärdade cookies i efterhand.

## Etikettskanning (LLM) - nuläge

Se [ADR 0012](docs/adr/0012-label-scanning-llm-integration.md) för
arkitekturen (port/adapter, `RestClient`, konfiguration via
miljövariabler).

- **`LabelInterpreter.interpret(...)` returnerar
  `Optional<InterpretedLabel>`** - `empty()` = totalt misslyckande
  (nätverksfel, LLM-fel, eller alla fem fälten `null`). Ett
  `InterpretedLabel` med enstaka `null`-fält är fortfarande ett
  LYCKAT resultat.
- **`LabelInterpretationService.interpretedFields()`** räknas ut från
  vilka av de fem fälten (namn, producent, årgång, land, region) som är
  icke-`null` i svaret - ingen separat boolesk flagga per fält.
- Etikettskanningens formulärfält döljs helt vid redigering
  (`th:if="${wine.id == null}"`) - bara relevant vid tillägg.
- Klientsidans nedskalning (Canvas, före uppladdning) är den enklaste
  av projektets JS-flöden - jämför med bulkimportens (se nedan), som
  också behöver hantera formatval för transparens.
- Statusraden "Fyllde i: ..." byggs från en FAST fältordning
  (`INTERPRETED_FIELD_ORDER`), inte ett `HashSet`s iterationsordning -
  annars blir meddelandet icke-deterministiskt mellan körningar.

## Flera användare - nuläge

Se [ADR 0013](docs/adr/0013-multi-user-accounts.md). `User`/
`User.UserId` (`domain/`) + `UserRepository`-port (JPA + InMemory-
adaptrar). `Wine.owner` (`User.UserId`) är en vanlig record-komponent -
en redigering (`existing.toBuilder()...`) bär automatiskt vidare rätt
ägare; en ny post stämplas explicit
(`.owner(currentOwner(authentication))`) i `WineController`.
`WineService.save(Wine)` tar medvetet inget owner-argument - all
ägarlogik sitter i anropande kod.

`owner_id` är `NOT NULL` i databasen (satt direkt i `schema.sql`, inte
via `@JoinColumn(nullable = false)` - se Kända fällor om varför).
`WineEntity.owner` är `FetchType.EAGER` (inte `LAZY`) - se Kända fällor.

**`User.defaultMinQuantityFilter` (WINE-41, semantiken ändrad från
"fler än" till "minst" i WINE-42)** är vinlistans sparade "Antal flaskor
minst"-standardval - default 1 för ett nytt konto (se
`RegistrationService`), redigerbart i Inställningar
(`SettingsController`/`installningar.html`, `POST /installningar/
antal-flaskor-filter`). `WineController.wineCellar(...)` faller
tillbaka till det sparade värdet när `GET /` saknar en explicit
`minQuantity`-queryparameter - en explicit parameter (bokmärke, delad
länk, eller ett ändrat filterpanelsvärde för sessionen) åsidosätter
alltid det sparade valet, samma princip som `sort`/`direction`.
`SearchCriteria.minQuantity` är ett tröskelvärde (`wine.quantity() >=
criteria.minQuantity()` i `WineService.search(...)`, inklusive
tröskelvärdet självt - **ändrad från strikt "fler än" till "minst" i
WINE-42** efter att ett negativt värde visade sig vara ett obekvämt sätt
att se utdruckna viner igen; ett filter satt till 0 visar numera även
0-antal-viner, vilket gjorde 0 till byggarens naturliga "ingen
begränsning"-default), inte en facett som de övriga fälten i recorden.
Kolumnen `users.default_min_quantity_filter` lades till som NULLABLE i
`UserEntity`s annotering och skärptes till NOT NULL DEFAULT 1 (ändrad
från 0 i WINE-42, se `db/migrations/2026-09-08-bump-default-min-
quantity-filter-to-one.sql` för engångsmigreringen av redan existerande
kontons sparade värde) i `schema.sql` i stället - samma mönster som
`wines.owner_id`/`wines.quantity` (se Kända fällor om Hibernates
`ddl-auto: update`).
Filterpanelens "antal aktiva filter"-badge räknar bara med det här
filtret när det faktiska värdet (URL-parameter eller sparad default)
AVVIKER från användarens sparade default - annars hade badgen alltid
visat minst 1 för varje inloggad användare, även utan något aktivt val
i den aktuella sessionen.

Den fulla migreringsresan (Fas 1, WINE-9 till WINE-18: datamodell,
formulärinloggning, registrering, scopead vinlista, borttagning av
ADMIN/READONLY, produktionsmigrering av ~30 befintliga viner) finns i
`docs/devlog.md` - flera produktionsdeployer kraschade under vägen på
grund av Hibernate `ddl-auto: update`-begränsningar, se Kända fällor
nedan för den generella lärdomen.

## Excel-import/export - nuläge

Se [ADR 0014](docs/adr/0014-web-based-excel-import-export.md) och
README:s "Import och export av Excel-data" för kommandon/kolumnlayout.
Webbaserad (Fas 2), inte längre ett fristående CLI-verktyg - det gamla
`tools/import-excel/`-verktyget (se
[ADR 0010](docs/adr/0010-excel-tool-standalone-module.md), Superseded)
är borttaget. `WineRowParser`/`WineRowWriter`/`ImageMatcher` lever kvar
i `infrastructure/excel/`.

- **Export:** `GET /export/xlsx` (den inloggade användarens egna viner,
  sorterade på namn) och `GET /export/bilder.zip` (en fil per vin med
  bild, namngiven enligt bildnamnskonventionen nedan). Exporten är
  byte-exakt.
- **Import** sker i två steg: `POST /import` (torrkörning - parsar och
  dubblettkontrollerar, sparar INGENTING) och `POST /import/commit`
  (sparar faktiskt, enligt vald dubblettstrategi per dubbletttyp).
  Uppladdad fil/bildmapp mellanlagras i en temp-mapp på disk
  (`Files.createTempDirectory("winecellar-import-")`) - bara sökvägen
  ligger i `HttpSession`, inte bilddatan (se
  [ADR 0015](docs/adr/0015-bulk-import-images-lossy-jpeg.md) för
  varför: multipart-gräns och JVM-heap-belastning från okomprimerade
  bulk-uppladdningar).
- **Bildnamnskonvention (nuvarande regel, `ImageMatcher.fileNameStem`):**
  stammen byggs av VILKA fält som faktiskt är satta (producent, namn,
  årgång - namn alltid obligatoriskt), separerade med understreck
  (`<producent>_<namn>_<årgång>`, `<producent>_<namn>`, `<namn>_<årgång>`
  eller bara `<namn>`). Mellanslag *inom* ett fält bevaras - bara
  separatorn *mellan* fälten är understreck. **Ingen fallback till
  namn-bara matchning om raden har MINST ett identitetsfält
  (producent/årgång) satt** - hittas ingen fil som matchar den
  specifika stammen exakt kopplas ingen bild alls, istället för att
  gissa mot namnet (så att två viner med samma namn men olika
  producent/årgång aldrig kan råka dela fel bild). En rad helt utan
  identitet (bara namn) matchar fortfarande mot en namn-bara fil.
- **Bulkimportens bilder skalas ned och komprimeras klientsidan
  (Canvas) innan uppladdning** - normalt JPEG, men WebP (med PNG-
  fallback om webbläsaren inte kan koda WebP) om bilden har någon
  transparent/halvtransparent pixel. En export följt av en re-import
  ger alltså INTE bit-identiska bilder tillbaka, bara visuellt
  likvärdiga - medvetet, se
  [ADR 0015](docs/adr/0015-bulk-import-images-lossy-jpeg.md). Detta
  gäller bara bulkimporten - den vanliga enskilda bilduppladdningen
  (`vin-formular.html`) och etikettskanningen sparar aldrig en
  nedskalad bild.
- **Övergivna temp-importmappar städas** vid lyckad inloggning
  (`InteractiveAuthenticationSuccessEvent`, `PendingImportCleanup`,
  mappar äldre än 2 timmar) - se
  [ADR 0017](docs/adr/0017-login-triggered-temp-import-cleanup.md).
  `ImportController.preview()` tar dessutom alltid bort en egen,
  tidigare ej committerad temp-mapp innan en ny skapas.
- "Bild"-kolumnen i själva `.xlsx`-filen finns inte längre (borttagen i
  WINE-32, se [ADR 0011](docs/adr/0011-excel-image-roundtrip-dual-mechanism.md),
  Deprecated) - all bildhantering går via `/export/bilder.zip`/
  mappuppladdningen, inte via kalkylbladet.

## Kända fällor att vara uppmärksam på

- **Gherkin på svenska kräver `# language: sv`** som absolut första rad i
  varje `.feature`-fil.
- **Cucumber Expressions skiljer sig från reguljära uttryck på ett sätt
  som ger förvirrande felmeddelanden, inte bara "hittar ingen match".**
  `@När("... i (stigande|fallande) ordning")` (regex-stil alternation)
  matchar tyst ingenting - `|` är bara en vanlig bokstav i en Cucumber
  Expression. Cucumber Expressions egen alternationssyntax (`/`) kastar
  i sin tur "An alternation can not be used inside an optional" om den
  läggs innanför en parentes (parenteser betyder *valfri text*, inte en
  fångstgrupp). Lösning: undvik alternation helt - två separata
  `@När`-metoder som anropar samma privata hjälpmetod.
- **Två stegklasser som delar samma Gherkin-steg måste vara EN klass,
  inte två - annars pratar de med olika servicelager-instanser inom
  samma scenario.** Cucumber-JVM (utan DI-container) skapar en ny
  instans av VARJE stegklass per scenario och kör ALLA `@Before`-hooks
  från ALLA klasser vars steg förekommer i scenariot. Om två klasser
  var för sig gör `wineService = new WineService(new
  InMemoryWineRepository())` blir det två separata repository-instanser
  även inom samma scenario - ett vin sparat i den ena är osynligt för
  den andra. Lägg nära besläktade steg i samma klass istället.
  **Variant av samma fälla, mellan klasser i stället för inom en:** när
  två SKILDA stegklassers globala `@Before`-hooks rör samma tabeller i
  motsatta riktningar (en skapar det den andra raderar), räcker det
  inte att bara tvinga EN inbördes ordning mellan de två metoderna - en
  delad resurs som både måste tömmas OCH fyllas på i rätt ordning kan
  kräva att en av metoderna delas upp i flera `@Before(order = ...)`
  som interfolieras med den andra klassens hook (se `docs/devlog.md`,
  WINE-15, för det konkreta tredelade exemplet: viner → users → nytt
  testkonto).
- `junit-platform-suite-engine` måste vara ett explicit beroende, inte bara
  `junit-platform-suite`.
- **Mockito + nya JDK-versioner**: lås `mockito.version` och
  `net.bytebuddy:byte-buddy(-agent)` om `@MockBean` börjar ge "Byte Buddy
  could not instrument all classes" lokalt.
- **`cucumber-spring` kräver exakt en `@CucumberContextConfiguration`-klass
  så fort den finns på classpath** - annars kraschar hela Cucumber-suiten,
  inte bara de scenarier som faktiskt behöver Spring. Lägg inte till
  `cucumber-spring`/Testcontainers-Postgres förrän ett persistensscenario
  faktiskt skrivs - annars tvingas rena CRUD-scenarier boota en full
  Spring-kontext (och kräva en databas) helt i onödan.
- **`@WebMvcTest`-slice-tester ser inte `SecurityConfig` automatiskt.**
  Utan `@Import(SecurityConfig.class)` slår Spring Boots egen
  standardsäkerhet in istället - redan gröna kontrollertester börjar
  plötsligt få 401. Varje ny bean-parameter på en `@Bean`-metod i
  `SecurityConfig` måste dessutom speglas i ALLA `@WebMvcTest`-klasser
  som importerar den (t.ex. ett nytt `@MockBean UserRepository` när
  `userDetailsService` fick ett nytt beroende).
- **Playwright Javas `Playwright.create()` installerar alla tre
  webbläsarmotorer (Chromium, Firefox, WebKit), inte bara den som faktiskt
  används i testet.** Kör installationssteget utan att begränsa till en
  enskild motor (se README:s "Köra tester"), annars försöker drivrutinen
  ladda ner de saknade motorerna vid nästa `mvn verify`.
- **Clever Cloud injicerar apparens miljövariabler även i byggsteget, inte
  bara vid körning.** En riktig produktionshemlighet kan då plockas upp av
  `@Value(...)` under `mvn test` i stället för `application.yml`s lokala
  default, och ett hårdkodat testlösenord slutar matcha. Pinnar man
  testvärden med `@TestPropertySource(properties = "...")` slipper man
  överraskningen - gäller varje ny `@WebMvcTest`-klass som autentiserar
  med hårdkodade testuppgifter.
- **PowerShell trasslar till `-Dexec.args="<flera mellanslagsskilda
  värden>"`** på ett sätt som inte ger ett tydligt citattecken-fel, utan
  ett förvirrande "Plugin ... could not be resolved" från Maven. Bash
  hanterar samma syntax utan problem. Lösning: sätt flervärdesargument
  som miljövariabler istället och skicka bara ett enda värde (utan
  mellanslag) via `-Dexec.args`.
- **Utan `<meta name="viewport" content="width=device-width,
  initial-scale=1">` triggas aldrig CSS-brytpunkten på riktiga mobila
  webbläsare** - de renderar då mot en betydligt bredare virtuell yta
  (~980px, zoomat ut) istället för mot den faktiska skärmbredden.
- **Playwrights `setViewportSize(...)` ensamt räcker inte för att fånga
  ovanstående klass av bugg.** Chromium respekterar bara den
  mobilspecifika "ingen viewport-tagg → rendera brett och zooma ut"-
  kvirken när `isMobile(true)` är satt på kontexten. Sätt alltid
  `isMobile(true)` (och gärna `setHasTouch(true)`) på mobilkontexter i
  UI-tester som ska spegla en riktig telefon, inte bara en smal skärm.
- **`@Lob private byte[] fält` mappar till Postgres `oid` (large object)
  med Hibernates standardinställningar, inte `bytea`.** Syns inte i en
  end-to-end-verifiering som bara testar HTTP-beteendet (bytes stämmer
  ändå via JDBC) - bara genom att faktiskt inspektera kolumntypen
  (`\d <tabell>`). Risken är föräldralösa poster i `pg_largeobject`.
  Använd `@JdbcTypeCode(SqlTypes.VARBINARY)` för en riktig `bytea`-
  kolumn istället. Kom ihåg att kontrollera detta explicit för framtida
  `@Lob byte[]`-fält.
- **Hibernates `ddl-auto: update` är opålitligt för ALTER av en
  redan existerande kolumn** (typbreddning, NOT NULL-tillägg) **så fort
  en genererad kolumn (`GENERATED ALWAYS AS ... STORED`) beror på den
  kolumnen.** Flera produktionsdeployer kraschade i tur och ordning
  (`cannot alter type of a column used by a generated column`) när
  Hibernate fick skäl att göra en fullständig kolumngenomgång av
  `wines` - beteendet visade sig opålitligt/svårförutsägbart
  (styrs av intern `HashMap`-iterationsordning), inte en konsekvent
  bugg att fixa vid källan. Lärdomar, i tur och ordning: (1) gör en
  nödvändig typbreddning/skärpning SJÄLV via en explicit engångs-SQL-
  migrering, lita inte på att en enskild lyckad deploy bevisar att
  Hibernates ALTER faktiskt gick igenom; (2) om en genererad kolumn
  (som `search_vector`) riskerar att blockera framtida ändringar av de
  kolumner den beror på, underhåll den istället via en TRIGGER
  (vanlig `tsvector`-kolumn + `BEFORE INSERT OR UPDATE`-trigger) - en
  vanlig kolumn har ingen Postgres-begränsning mot att ALTER:a det den
  "hör ihop med". Se `docs/devlog.md` (WINE-10/WINE-15) för den
  fullständiga, tre rundor långa resan fram till den här slutsatsen.
- **Spring Boots `ScriptUtils` (kör `schema.sql` via `spring.sql.init.
  mode: always`) delar upp filen i separata JDBC-anrop genom enkel
  strängsökning efter `;`** - den förstår inte PL/pgSQL:s
  `$$...$$`-citerade funktionskroppar och kapar en
  `CREATE FUNCTION ... AS $$ ... $$`-sats mitt i vid första `;` inuti
  funktionskroppen. Lösning: `spring.sql.init.separator: ";;"` - varje
  toppnivåsats avslutas med `;;`, medan enstaka `;` inuti en
  dollar-citerad funktionskropp lämnas orörda och tolkas korrekt av
  Postgres självt.
- **`.vk-bildyta` (den breda kortvyns bildkolumn) kräver
  `position: absolute` för bilden/platshållaren, inte bara
  `min-height: 0`, för att undvika att en smal/hög bild tvingar upp
  hela radens höjd.** Grid-/flex-item har `min-height: auto` som
  standard, vilket låter deras eget innehålls naturliga
  bildförhållande-höjd räknas in i "auto"-radens höjd - särskilt
  märkbart när en smal/hög bild kombineras med ett kort med lite text.
  `min-height: 0` på containern räcker INTE (verifierat, kvarstod
  oförändrat). Den robusta lösningen är att ta bilden helt ur
  dokumentflödet (`position: absolute; inset: 0` på en `position:
  relative`-container) - absolutpositionerade element kan aldrig bidra
  till en förälders/grid-radens automatiska storleksberäkning.
  **Testmetod värd att komma ihåg om den här CSS:en någonsin rörs
  igen:** verifiera alltid med BÅDE en ovanligt smal/hög testbild OCH
  ett vin med minimal text samtidigt - varken "Ingen bild"-
  platshållaren eller en "typisk" bild/textkombination avslöjar buggen.
- **Session-/remember-me-cookiens `Secure`-flagga förutsätter att appen
  faktiskt VET att anropet gick över HTTPS (WINE-40, kodgranskningsfynd,
  löst i två steg i WINE-43 - se ADR 0020).**
  Spring Security sätter `Secure` baserat på om requesten "är säker"
  enligt servletcontainern, vilket bara stämmer om appen själv
  terminerar TLS. Clever Cloud terminerar TLS i en framförliggande
  reverse proxy/load balancer och vidarebefordrar ett vanligt HTTP-anrop
  internt, med det ursprungliga protokollet signalerat via en
  `X-Forwarded-Proto`-header - bekräftat av Clever Clouds egen
  dokumentation (deras plattformsdokumentation för applikationsruntimes
  beskriver load balancern framför appen och anger explicit att
  `X-Forwarded-Proto` alltid är satt på plattformen; deras tekniska
  blogg beskriver samma sak för JVM-baserade ramverk specifikt).
  `server.forward-headers-strategy: framework` är därför satt i
  `application.yml` - ramverkets (Spring Framework/spring-web, inte
  Spring Security) `ForwardedHeaderFilter` läser headern.
  **Automatiskt bekräftat av `ForwardedHeadersIT` (WINE-43) mot en
  riktig inbäddad servletcontainer, med ett verkligt, förvånande
  resultat i två delar, inte en:** remember-me-cookien (skriven av
  Spring Securitys eget applikationslager, som läser requestens
  "är säker"-status via samma request som filtret satte upp) FÅR
  `Secure` satt korrekt. Den vanliga sessionscookien (JSESSIONID) FÅR
  DET INTE, trots samma header - Tomcat skapar och skriver den cookien
  via sin egen, interna request-hantering, som ligger utanför/före den
  Spring-nivå-wrappring `ForwardedHeaderFilter` sätter upp; bara en
  servletcontainer-nivå-lösning (den "native"-strategin, som kräver
  Clever Clouds interna proxy-IP-intervall - se nästa stycke för varför
  den avvisades) hade åtgärdat även sessionscookien via samma mekanism.
  Den ursprungliga WINE-43-dokumentationen påstod felaktigt att båda
  cookies skyddades av `framework`-strategin ensam, ett antagande som
  aldrig var testat förrän detta automatiska test skrevs.
  **Löst (samma story, efter eskalering till arkitekt och användaren):**
  sessionscookien tvingas nu säker explicit via
  `server.servlet.session.cookie.secure: true`, men BARA i en ny
  `application-prod.yml`-profil - inte i huvudkonfigurationen, eftersom
  en generell inställning hade gjort lokal `mvn spring-boot:run` över
  vanlig HTTP obrukbar (en webbläsare skickar aldrig en säkert flaggad
  cookie tillbaka över en osäker anslutning, så inloggningen hade sett
  ut att fungera men aldrig hållit i sig mellan requester). **Kräver ett
  manuellt steg i drift: miljövariabeln `SPRING_PROFILES_ACTIVE=prod`
  måste sättas i Clever Cloud-konsolen** för att profilen faktiskt ska
  aktiveras - den slår inte på sig själv bara för att appen körs i
  produktionsmiljön, och ingen tidigare story har satt den. Utan den
  miljövariabeln fortsätter sessionscookien vara osäker i produktion,
  precis som innan den här fixen. Verifierat av `ForwardedHeadersIT`
  under båda profilerna: sessionscookien blir säker med `prod`-profilen
  aktiv (och det stående `X-Forwarded-Proto`-behovet för remember-me-
  cookien kvarstår oförändrat), och förblir osäker i default-profilen så
  att lokal HTTP-utveckling inte går sönder.
  **Medvetet accepterad risk (samma story, se ADR 0020):**
  `framework`-strategin litar på `X-Forwarded-Proto` från VILKEN
  källa som helst, utan någon motsvarighet till den käll-IP-allowlist
  som servletcontainerns egen ("native") strategi hade haft. Skulle
  Clever Clouds proxy någon gång inte strippa en klients egen sådan
  header, eller skulle appen bli nåbar förbi proxyn, kan en förfalskad
  header i teorin få `Secure`-flaggan att sättas felaktigt - men
  webbläsare avvisar ändå en `Secure`-flaggad cookie som tas emot över
  ett faktiskt osäkert (HTTP) svar, vilket begränsar den praktiska
  skadan. Bedömt som en rimlig avvägning för appens nuvarande skala
  och avsaknad av känsliga data, inte en brist som ska åtgärdas senare.
  **Kompletterande, separat accepterad risk (upptäckt vid en
  uppföljande kodgranskning, samma story, se ADR 0020).**
  `ForwardedHeaderFilter` litar på samma sätt på `X-Forwarded-Host`/
  `-Port`/`-Prefix`, utan källbegränsning - vilket i teorin kan påverka
  Spring Securitys omdirigeringsmål direkt efter en lyckad inloggning
  (`DefaultSavedRequest`). Täcks INTE av föregående styckes
  cookie-specifika resonemang, så det är ett separat, kompletterande
  beslut, inte en förlängning av det. Bedömd praktisk skada är ändå
  LÄGRE än cookie-risken - exploatering kräver att angriparen
  kontrollerar headrarna i offrets egen förfrågan, inte bara en klickad
  länk som ett klassiskt öppet omdirigeringsproblem. Medvetet
  accepterat, samma avvägning som ovan.
