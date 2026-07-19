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
responsiviteten, se "UI-test" nedan - `vinkallare.html` renderar två
kortlayouter i samma HTML-fragment och växlar mellan dem med en CSS media
query vid 960px, verifierat av `WineListResponsiveIT`: breda kort på
desktop (`#vinlista-tabell` - namnet är kvar av historiska skäl, se
"Tabellvyns designomgång" nedan) och smala kort med en infälld
"Detaljer"-sektion på mobil (`#vinlista-kort`).

`Wine` har vuxit till 23 fält i takt med att Excel-importen (se nedan)
krävde dem - för många för en läsbar positionell record-konstruktor, så
`Wine.builder()...build()` används på alla anropsplatser istället för
`new Wine(...)`. Alla fält är redigerbara i webb-UI:t via en separat
sida (`vin-formular.html`) - för mycket för en radform i listan, så det
är en egen sida istället för ett htmx-fragment som resten av appen.
Startsidan (`/`) är bara vinlistan; att lägga till och redigera ett vin
sker på **samma** sida/mall (`GET /wines/nytt` respektive
`GET /wines/{id}/redigera`) - fälten är identiska, det enda som skiljer
är rubrik, submit-knapp och vart formuläret postar (`POST /wines` vid
tillägg, `POST /wines/{id}/redigera` vid redigering), så två nästan
identiska mallar hade bara varit dubblettunderhåll. Formuläret postar
`multipart/form-data` och tar emot en valfri bildfil (`bild`) tillsammans
med alla andra fält i samma spar-anrop - ett tomt filfält skriver inte
över en redan sparad bild. Ändra antal och ladda upp bild är alltså inte
längre separata snabbåtgärder i listan; bara "Ta bort" är kvar som
htmx-fragment där.

Vinlistan visar en överblick per vin i grundläget: bild, namn, typ,
producent, land, region, underregion, druvor, årgång, flaskor, eget
betyg, Munskänkarnas betyg och Vivino-betyg (`plats` flyttades härifrån
till detaljvyn 2026-07-19, se nedan). Övriga fält (plats, inköpsdatum,
pris, inköpsanledning, tasting notes, Systembolagets
produktnummer/beskrivning, Munskänkarnas bedömning, annan referens -
alltså allt utom `id`, `image`/`image_mime_type` och de ännu obyggda
`created_at`/`updated_at`) visas infällt under en "Detaljer"-knapp per
rad/kort med `<details>`, ingen JS. Fälten delas mellan tabell- och
kortvyn via ett gemensamt Thymeleaf-fragment (`detaljfalt(vin)`) istället
för att dupliceras, och de flesta av dem visas bara om de faktiskt är
satta (`th:if="${vin.X != null}"`) - plats är alltid satt (obligatoriskt
fält) och visas därför ovillkorligt, som resten av översiktsfälten.
I tabellvyn ligger den infällda detaljraden i en egen `<tr>` med
`colspan` som spänner hela tabellbredden, inte i en av översiktens
smala kolumner - annars klämdes de uppfällda fälten in i en enda smal
kolumn även på en stor skärm (upptäckt av användaren mot den riktiga
deployen, fixat 2026-07-19).

**Kortmallen fick en egen designomgång (2026-07-19)**, styrd av en PNG-
mockup användaren ritade upp (bild vänster, text höger, inga fältetiketter
för de flesta fälten). `.vinkort-topp` är en flex-rad med en smal
bildkolumn (`.vinkort-bildyta`, `flex: 0 0 5.5rem`) och en textkolumn
till höger - producent visas utan egen etikett, namn och årgång på samma
rad (`<strong>Namn</strong> Årgång`), ursprung (land/region/underregion)
som en löpande rad utan fältetiketter, vintyp inom parentes på egen rad
med extra luft ovanför, druvor på egen rad. Betygen (eget, Munskänkarnas,
Vivino), "Detaljer" och knapparna (Redigera/Ta bort) ligger däremot
**utanför** `.vinkort-topp`, som egna block direkt under `.vinkort` -
de spänner alltså hela kortets bredd istället för att trängas i den
smala textkolumnen. Ändringen kom efter att användaren påpekade att
bilden (t.ex. en flaskfoto) ofta slutar ungefär vid druvor-raden, så
utrymmet under bilden stod oanvänt när resten av kortet klämdes in i
textkolumnen bredvid. Betygen grupperas under en egen rubrikfri sektion
med etiketten *ovanför* sitt värde (inte på samma rad) - annars radbröts
långa betygstexter (t.ex. Munskänkarnas fulla etikett) mitt i på ett
sätt som såg trasigt ut. Antal flaskor är inte med i mockupen men
löstes som en rund badge i kortets övre högra hörn (`.flaskor-badge`,
`position: absolute`) efter en avstämning med användaren - central
information för en vinkällarapp som annars hade krävt en utfällning
för att se. "Detaljer" är en vanlig `<summary>` men styld som en
understruken länk (`text-decoration: underline; font-weight: normal`)
istället för tabellvyns fetstilta knapputseende, för att matcha
mockupens länkkänsla. Tabellvyn var vid det här laget fortfarande
oförändrad - designomgången var avgränsad till kortmallen. Tabellvyn
fick sin egen omgång strax efter, se "Tabellvyns designomgång" längre
ner - och ersattes då helt av samma sorts kort, fast bredare.

**Redigera/Ta bort flyttade till Detaljer, högerjusterade (2026-07-19,
gäller både tabell- och kortvyn):** låg tidigare alltid synliga i
översikten (en egen kolumn i tabellen, `.vinkort-fot` under kortet).
Ligger nu istället längst ner i den infällda "Detaljer"-sektionen, i en
delad `.detalj-atgarder`-`<div>` (`display: flex; justify-content:
flex-end`) - samma klass återanvänds i både tabellens `<td colspan>`
och kortets `<details>` istället för att duplicera layouten. I
tabellvyn försvann därmed hela åtgärdskolumnen ur `<thead>`/huvudraden,
så `colspan` på detaljraden sänktes från `14` till `13` för att matcha
det nya antalet översiktskolumner (håll dem i synk om en kolumn läggs
till eller tas bort).

**Detaljer-fältens ordning justerad, bara för kortvyn (2026-07-19):**
den nya ordningen är Inköpsdatum, Pris, Plats, Varför köpt, Tasting
notes, Systembolagets beskrivning, Munskänkarnas bedömning (Annan
referens ligger kvar sist, oförändrad). De fyra sista (Varför köpt,
Tasting notes, Systembolagets beskrivning, Munskänkarnas bedömning)
visar dessutom värdet *under* etiketten istället för bredvid den -
Varför köpt fick samma behandling i en uppföljande justering samma
dag, av samma skäl som de tre första.
Löst utan att duplicera `detaljfalt`-fragmentet eller ändra dess
DOM-ordning: varje `dt`/`dd`-par har fått en `fd-*`-klass (t.ex.
`fd-inkopsdatum`), och CSS `order` (plus `grid-column: 1 / -1` för de
fyra som ska staplas) sätts bara under `.vinkort dl`-selektorn - så
samma fragment kan fortsätta återanvändas av tabellens
`.detaljlista-bred`, som behåller sin egen (ursprungliga)
dokumentordning helt opåverkad av kortvyns omordning.

**Systembolagets produktnummer slogs ihop med beskrivningsraden
(2026-07-19), i både tabell- och kortvyn:** visades tidigare som en
egen `dt`/`dd`-rad (`fd-sb-nummer`); den klassen och raden är nu
borttagna. Produktnumret står istället inom parentes direkt efter
etiketten "Systembolagets beskrivning" (`Systembolagets beskrivning
(12345)`) - till skillnad från ordningsjusteringen ovan gäller den här
ändringen båda vyerna, eftersom det är en innehållsändring i själva
`detaljfalt`-fragmentet, inte en CSS-scopead layoutskillnad. **Om
beskrivningen saknas visas produktnumret inte alls** - det finns ingen
etikett kvar att fästa parentesen på, så `dt`/`dd`-paret försvinner
helt i det fallet (`th:if="${vin.systembolagetDescription != null}"`
styr båda). Medvetet vald avvägning, inte ett förbiseende - om det
visar sig vara ett problem i praktiken (produktnummer utan beskrivning
förekommer) är det en enkel ändring att lägga till en fallback-rad för
det fallet.

**Tabellvyns designomgång (2026-07-19/20) - den gamla `<table>` är helt
borttagen.** Styrd av en PNG-mockup användaren ritade upp
(`Vinlista.png`) och en Artifact-jämförelse som itererades i flera
omgångar (dämpade labels, betygsraden flyttad upp bredvid bilden,
omordning, labels linjerade på samma höjd, fasta betygskolumnbredder)
innan den byggdes på riktigt. Beslutet var uttryckligen att **inte**
ha någon infälld Detaljer på desktop - alla fält visas alltid,
inklusive `otherReference` ("Annan referens"), som varken den gamla
tabellen eller kortvyns Detaljer råkade visa förut (ett fält som fanns
i datamodellen men aldrig syntes någonstans i listan - upptäcktes när
allt skulle visas samtidigt).

- **`#vinlista-tabell` innehåller nu breda kort (`.vinkort-bred`),
  inte en `<table>`** - namnet på `id`:t/klassen är kvar av historiska
  skäl (CSS-brytpunkten och `WineListResponsiveIT` pekar redan på det),
  men det är samma sorts kort som `#vinlista-kort` (mobil), bara
  bredare och utan `<details>`. `vinbild-tabell`, `.detaljlista-bred`
  och `<tr class="detaljrad"> / colspan` (dokumenterat ovan) är alla
  borttagna tillsammans med `<table>`:n.
- **Fyra kolumner delas av tre radgrupper** (`.vk-topp`, `.vk-info-rad`,
  `.vk-text-rad`) via samma `grid-template-columns`, så Inköpsdatum
  hamnar under bilden, Pris under textblocket, Varför köpt under
  Munskänkarna och Plats under Eget betyg. Varje fält har ett
  **explicit `grid-column`** (inte auto-placering) - annars skulle t.ex.
  Pris hoppa in i Inköpsdatums kolumn för ett vin som saknar
  inköpsdatum, eftersom CSS Grids auto-placering fyller nästa lediga
  cell i dokumentordning oavsett vilket fält som faktiskt saknas.
- **Betygsraden (Vivino/Munskänkarna/Eget betyg) är en egen grid-rad**
  (`grid-row: 2`) bredvid bilden, som spänner båda raderna
  (`grid-row: 1 / 3`) och stretchar till samma höjd. Alla tre labels
  börjar därför på exakt samma höjd oavsett hur långt respektive värde
  råkar vara.
- **Munskänkarna/Eget betyg-kolumnerna har fast bredd (`18rem`), inte
  `fr`** - de måste rymma det längsta möjliga betygsvärdet (någon av
  de 29 Rating-etiketterna, t.ex. `"12,5 (12 - 14,5 Bra till mycket
  bra vin)"`) oavsett vilket av de två fälten som råkar ha ett långt
  värde. Verifierat med båda fälten satta till den längsta etiketten
  samtidigt.
- **Sidan blev bredare för att detta skulle få plats:** `body`s
  `max-width` höjdes från `48rem` till `70rem`, och CSS-brytpunkten
  mellan bred kortvy och mobil kortvy höjdes från `640px` till `960px`
  - de fasta 18rem-kolumnerna kan inte krympa, så under ~960px svämmar
  layouten över om inte mobilvyn tar över istället. Verifierat manuellt
  vid 900px (mobilkort, ingen överflödning) och 1280px (breda kort,
  inga betygsvärden radbryter).
- Redigera/Ta bort ligger direkt i kortet (`.detalj-atgarder`, samma
  klass som kortvyn återanvänder för sin infällda variant) - inte
  infällt bakom något klick, eftersom hela poängen med omgången var att
  slippa en Detaljer-sektion på desktop.

**Bildens storlek i de breda korten justerad i tre omgångar
(2026-07-20)**, efter att användaren tyckte den var onödigt stor.
`.vk-bildyta`s kolumnbredd (`6rem`) rördes **inte** i någon av
omgångarna - den delas med Inköpsdatum i `.vk-info-rad`, som behöver
bredden för att inte radbryta datumvärden. Istället fick själva
bilden/platshållaren ett eget `max-width`/`max-height` (mindre än sin
kolumn, med tomrum till höger). Första försöket (`max-width: 3.5rem;
max-height: 5rem`, `grid-row: 1` - bara textblockets rad) visade sig
vara för litet - användaren tyckte kortvyns bildstorlek
(`.vinbild-kort`, `flex: 0 0 5.5rem`-kolumn, `max-height: 12rem`) såg
bättre ut. Justerat till `max-width: 5.5rem; max-height: 8rem` - bättre,
men fortfarande upplevt som lite för litet, och toppjusteringen
(`align-self: start`) gjorde att bildens underkant inte hamnade i
linje med något särskilt. Tredje omgången: `.vk-bildyta` spänner
återigen båda raderna (`grid-row: 1 / 3`, som i den allra första,
"för stora" versionen) men med `align-self: end` istället för
`stretch` - bilden bottenjusteras mot betygsradens underkant (samma
höjd som Vivino-värdet) men **behåller sin egen begränsade storlek**
(nu `max-width: 6rem; max-height: 9rem`) istället för att sträckas för
att fylla hela den spända ytan. Det är skillnaden mellan `stretch`
(vad som gjorde bilden "för stor" i original­versionen - fyller hela
ytan oavsett hur hög den är) och `end` (positionerar en begränsad
storlek vid nederkanten av ytan) som gör att båda kraven - liten bild
**och** bottenjusterad mot betygsraden - kan uppfyllas samtidigt.
Verifierat manuellt vid 1280px med både ett vin med lång text och ett
med kort text: bildens underkant linjerar med Vivino-värdet i båda
fallen, och Inköpsdatum/betygsvärdena radbryter fortfarande inte.

**Fjärde omgången: både överkant och underkant, tillbaka till
`stretch`** - användaren ville att bilden även skulle linjera mot
producentnamnets överkant, inte bara mot Vivino-värdets underkant.
Att vara alignad i **båda** ändarna samtidigt går bara med `align-self:
stretch` (bilden fyller hela den spända ytan, `grid-row: 1 / 3`) -
`end` (föregående omgång) och `start` (omgången innan dess) kan bara
träffa en kant i taget, eftersom en begränsad storlek som inte fyller
hela ytan alltid lämnar tomrum någonstans. `.vk-bildyta img`/
`.vk-bildplatshallare` gick från `max-height` till `height: 100%`
(fortfarande `max-width: 6rem` för bredden) - `object-fit: contain`
skalar innehållet proportionerligt utan distorsion, men själva ytan
(och därmed hur hög bilden faktiskt blir) växer nu med hur mycket text
vinet har, en medveten avvägning för att kunna alignas mot båda
kanterna. `<a>`-taggen som omsluter `<img>` fick också
`display: block; height: 100%` - annars bryts `height: 100%`-kedjan
eftersom en vanlig inline `<a>` inte har någon egen resolverbar höjd.
Verifierat manuellt vid 1280px med både lång och kort text: bilden
linjerar mot båda kanterna i båda fallen, och ingenting annat radbryter.

**Femte omgången: `object-position: bottom` - en riktig flaskbild följde
inte underkanten trots `height: 100%`.** Upptäckt av användaren mot den
riktiga deployen (skärmdump bifogad): en riktig bild (till skillnad
från "Ingen bild"-platshållaren) centrerades istället inom sin box
(standard `object-position: 50% 50%`) när bildens eget höjd/bredd-
förhållande inte fyllde hela den spända ytan - det lämnade tomrum både
ovanför **och** under bilden, inte bara ovanför. Platshållarrutan har
inget eget bildförhållande och fyller alltid hela sin box trivialt, så
den lokala testningen (som bara använt "Ingen bild"-vinet) missade
buggen helt - fixat genom att faktiskt ladda upp en riktig (genererad)
flaskbild lokalt och verifiera mot den, inte bara platshållaren.
Fixat med `object-position: center bottom` på `.vk-bildyta img` - tvingar
`object-fit: contain` att alltid lägga eventuellt överskottsutrymme
högst upp istället för att dela det mellan topp och botten.
**Kvarstående avvägning, inte en bugg:** eftersom en riktig bild har
ett fast bildförhållande som inte alltid matchar boxens (som växer med
textmängden), kan bilden bara garanterat nå **en** kant fullständigt -
underkanten (mot Vivino-värdet, det uttryckliga kravet) prioriterades.
Överkanten kan fortfarande ha ett litet tomrum ovanför för viner med
mycket text, där boxen blir högre än vad bildens eget
bredd/höjd-förhållande kräver.

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

**Bilder i `bytea`, inte objektlagring:** medvetet val för en samling i
den här storleksordningen (se diskussion i chatten) - en datakälla,
enklare backup, ingen extra molntjänst. Om samlingen och bildmängden växer
kraftigt är det en isolerad migrering senare (flytta bara bilddatan), inte
något vi bygger beredskap för nu.

**`oid`-avvikelsen är fixad (2026-07-17):** `image`-kolumnen blev i
praktiken `oid` (Postgres large object) istället för `bytea` -
`@Lob private byte[] image` mappar till `oid` med Hibernates
standardinställningar mot Postgres, upptäckt via `\d wines` (syntes inte
i den ursprungliga end-to-end-verifieringen, som bara testade
HTTP-beteendet). `WineEntity.image` har bytt från `@Lob` till
`@JdbcTypeCode(SqlTypes.VARBINARY)`, som ger en riktig `bytea`-kolumn.
`ddl-auto: update` kan bara lägga till kolumner/tabeller, inte ändra en
kolumns typ, så en engångsmigrering krävdes: `db/migrations/2026-07-17-image-oid-to-bytea.sql`
kopierar bilddata från de gamla large objects till en ny `bytea`-kolumn,
städar bort large objects med `lo_unlink` (annars läcker de) och byter
namn på kolumnen. Verifierat lokalt mot en simulerad "gammal" databas -
bytes bevarade, `pg_largeobject` tomt efteråt.

Körs en gång, manuellt, mot en riktig databas (samma mönster som
Excel-importen ovan):

```powershell
$env:POSTGRESQL_ADDON_HOST = "<host>"
$env:POSTGRESQL_ADDON_PORT = "<port>"
$env:POSTGRESQL_ADDON_DB = "<databasnamn>"
$env:POSTGRESQL_ADDON_USER = "<användare>"
$env:POSTGRESQL_ADDON_PASSWORD = "<lösenord>"

Get-Content db\migrations\2026-07-17-image-oid-to-bytea.sql -Raw |
  docker run --rm -i -e PGPASSWORD=$env:POSTGRESQL_ADDON_PASSWORD postgres:16 `
    psql -h $env:POSTGRESQL_ADDON_HOST -p $env:POSTGRESQL_ADDON_PORT `
         -U $env:POSTGRESQL_ADDON_USER -d $env:POSTGRESQL_ADDON_DB
```

**Körd mot produktionsdatabasen (2026-07-17):** `UPDATE 0` och `lo_unlink`
gav 0 rader - inga bilder hade laddats upp i produktion än, så
migreringen var en ren typkonvertering utan data att flytta. `image` är
nu `bytea` i produktion.

**Uppladdning och visning (byggt):** bilden är sedan sist ett vanligt fält
i `vin-formular.html` (fältnamn `bild`, `enctype="multipart/form-data"`)
och sparas i samma `WineService.save`-anrop som resten av vinet - ett tomt
filfält skriver inte över en redan sparad bild. `GET /wines/{id}/bild`
serverar bytes tillbaka med `Content-Type` satt från `image_mime_type`
(404 om vinet saknar bild). `vinkallare.html` visar en `<img>`-tagg mot
den GET-routen när `vin.harBild()` är sant, annars en textplatshållare -
bilddatan skickas alltså aldrig inbäddad i själva listfragmentet, bara via
webbläsarens egna bildförfrågningar. Miniatyrerna i listan (tabell och
kort) skalas med `object-fit: contain` - beskär alltså aldrig bort delar
av etiketten - och är länkade till samma GET-route, så ett klick öppnar
bilden i sin fulla storlek via webbläsarens egen bildvisning (ingen
egenbyggd lightbox/JS).
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
  Testcontainers-Postgres, oberoende av Cucumber-suitens. Mobilkontexten
  sätter `isMobile(true)`, inte bara en smal `setViewportSize` - se
  CLAUDE.md för varför det gör skillnad (en riktig telefon visade
  tabellvyn trots grönt test, innan `<meta name="viewport">` och
  `isMobile(true)` fanns).
- Kräver `com.microsoft.playwright:playwright` som testberoende, samt att
  webbläsarbinärerna installeras en gång lokalt (och som ett steg i CI
  innan `mvn verify`):
  ```
  mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.classpathScope=test -Dexec.args="install"
  ```
- **Utökad efter kortmall-/Detaljer-omdesignen (2026-07-19)** med
  `WineListResponsiveIT`-tester för att Redigera/Ta bort är dolda tills
  "Detaljer" fälls ut, och att flaskbadgen visar rätt antal - inget av
  detta kan `@WebMvcTest`/MockMvc verifiera, eftersom det är CSS
  (`<details>` utan `open`) som gömmer innehållet, inte serverlogik.
  `WineControllerTest` fick samtidigt ett strukturellt test
  (`skaRenderaKortvynsNyaStruktur`) som verifierar att klassnamnen
  kortets utseende är beroende av faktiskt finns i den renderade
  HTML:en (`flaskor-badge`, `vinkort-producent`, `vinkort-namn`,
  `betyg-label`/`betyg-varde`, `fd-*`, `detalj-atgarder`) - ett rent
  strukturellt test, inte ett bevis på att CSS:en renderar rätt (det
  täcks av Playwright-testerna för synlig/dold-beteende, inte
  pixel-exakt layout).
- **Uppdaterad efter tabellvyns designomgång (2026-07-19/20):** de
  breda korten (`#vinlista-tabell`) har ingen Detaljer längre, så
  `skaDöljaRedigeraOchTaBortTillsDetaljerFällsUtPåDesktop` byttes mot
  `skaVisaRedigeraOchTaBortDirektPåDesktop` (inget klick behövs -
  åtgärderna är synliga direkt) plus
  `skaVisaAllaFältDirektPåDesktopUtanAttFällaUtNågot` (ett fält som
  `tastingNotes` är synligt utan interaktion). Readonly-kontots test
  (`skaDöljaLäggTillRedigeraOchTaBortFörReadonlyKontot`) tappade sitt
  klick på `"Detaljer"` av samma skäl. `WineControllerTest` fick ett
  nytt strukturellt test (`skaRenderaBredaKortMedAllaFältSynliga`) som
  verifierar att `<table>` och `vinbild-tabell` är helt borta, att de
  nya `vk-*`-klasserna finns, och att alla fält - inklusive `Annan
  referens`, som aldrig syntes i den gamla tabellen eller kortvyns
  Detaljer - renderas.

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

Kör en gång, manuellt, mot en riktig databas. **I PowerShell** - sätt
anslutningen som miljövariabler (samma namn som `application.yml` läser)
och skicka bara filsökvägen som argument, annars trasslar PowerShells
citattecken-hantering till ett `-Dexec.args` med flera mellanslagsskilda
värden (Maven kan då tolka delar av strängen som ett plugin-koordinat och
misslyckas med ett förvirrande "could not be resolved"-fel som inte har
med själva filen eller databasen att göra):

```powershell
cd C:\projects\winecellar
mvn install -DskipTests                      # från repo-roten, en gång

$env:POSTGRESQL_ADDON_HOST = "<host>"
$env:POSTGRESQL_ADDON_PORT = "<port>"
$env:POSTGRESQL_ADDON_DB = "<databasnamn>"
$env:POSTGRESQL_ADDON_USER = "<användare>"
$env:POSTGRESQL_ADDON_PASSWORD = "<lösenord>"

cd tools\import-excel
mvn exec:java "-Dexec.args=C:\Users\jonsw\Documents\Vin\Vinlista.xlsx"
```

**I Bash** funkar det multi-värdesargumentet som tidigare stod här:

```bash
cd tools/import-excel
mvn exec:java -Dexec.args="<sökväg-till-Vinlista.xlsx> <jdbc-url> <användare> <lösenord>"
```

Utan `jdbc-url`/`användare`/`lösenord` som argument används
`POSTGRESQL_ADDON_*`-miljövariablerna, annars
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
30 rader importerade (2 dåvarande ofullständiga utkastrader korrekt
överhoppade), alla fält - inklusive betyg, Systembolagets hopklistrade
cell och prisceller med extra anteckningstext - stämde vid stickprov mot
källfilen, och appen renderade listan felfritt efteråt.

**Körd mot produktionsdatabasen (2026-07-17):** kördes lokalt (PowerShell,
se kommandot ovan - Clever Cloud har inget CLI att köra verktyget *på*,
och behövs inte heller, Postgres-tillägget är nåbart utifrån) mot
produktionens `POSTGRESQL_ADDON_*`-uppgifter från Clever Cloud-konsolen.
Sparade 30 viner utan fel - samtliga rader i källfilen hade alltså hunnit
fyllas i komplett sedan den lokala testkörningen ovan. Verktyget har ingen
dedupliceringslogik - kör inte importen igen mot samma databas, det skulle
skapa dubbletter.

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
- [x] Responsiv dubbel kortmall + `WineListResponsiveIT` - `vinkallare.html`
      växlar mellan breda kort (desktop, `#vinlista-tabell`) och smala kort
      med infälld Detaljer (mobil, `#vinlista-kort`) vid 960px, verifierat
      med Playwright i flera viewport-bredder. Startade som en
      tabell/kort-uppdelning vid 640px, men tabellvyn ersattes senare helt
      av breda kort utan Detaljer (se "Tabellvyns designomgång" ovan)
- [x] Bilduppladdning och -visning (`image` + `image_mime_type`,
      del av `vin-formular.html`) - se Datamodell ovan för `oid`-avvikelsen
- [x] Excel-importskript (`tools/import-excel/`) - fristående Maven-modul,
      `Wine` utökad till 23 fält (`Rating`-enum m.m.) för att rymma hela
      Vinlista.xlsx, körd mot produktionsdatabasen - se "Import av
      befintlig Excel-data"
- [x] Autentisering (se CLAUDE.md:s "Säkerhet") - HTTP Basic på hela appen,
      inte bara en admin-del, eftersom det inte finns någon publik läsvy
      här och appen redan var nåbar från nätet
- [x] Deploy till Clever Cloud (se "Deploy" ovan) - appen GitHub-länkad,
      verifierad fungerande mot en riktig Postgres
- [x] Readonly-konto (`readonly`/`readonly`, se CLAUDE.md:s "Säkerhet") -
      får se listan och bilder men nekas lägg till/redigera/ta bort både
      i UI:t (dolda länkar/knappar) och på serversidan
      (`SecurityConfig`, `hasRole("ADMIN")` på formulär-/ändringsrouterna) -
      så det inte går att komma åt funktionaliteten genom att gissa på
      URL:en, verifierat av både `WineControllerTest` och
      `WineListResponsiveIT`
