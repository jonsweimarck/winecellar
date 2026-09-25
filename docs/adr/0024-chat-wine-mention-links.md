# 0024: AI-chattens svar länkar till nämnda viner i vinlistan

## Status

Accepted (2026-09-25)

## Context

AI-chatten om vinsamlingen (se
[0021](0021-wine-chat-conversational-llm-integration.md)) svarar ofta med
konkreta vinförslag hämtade ur användarens egen samling - men svaret är
bara text. Att faktiskt hitta ett nämnt vin i vinlistan kräver idag att
användaren själv byter sida och skriver in namnet i sökfältet. Ju mer
konversationsbaserad chatten blir, desto oftare uppstår det här steget.

Assistentens svar är i grunden opålitlig, fri text från en extern tjänst
(se [0021](0021-wine-chat-conversational-llm-integration.md) och den
befintliga markdown-till-HTML-tolkningen) - vilken text som helst kan
förekomma, inklusive text som råkar likna, men inte är, ett av
användarens vinnamn. Länkningen måste därför vara EXAKT mot den
inloggade ägarens FAKTISKA vinnamn, inte en gissning.

## Decision

Två saker läggs till svarets rendering:

1. Varje förekomst av ett av ägarens vinnamn i assistentens svar blir en
   länk till en exakt namnsökning i vinlistan - samma facett (se punkt 2
   nedan) som samlingslänken använder, inte en fritextsökning. Vinnamnet
   är redan känt EXAKT (det är hämtat direkt ur ägarens egen
   kandidatlista, inte gissat ur svarstexten), så det finns ingen
   anledning att gå via en bredare, ordstammad fritextsökning - som
   dessutom visade sig ge en missvisande, "+"-mellan-orden-uppdelad
   filterchip (avsedd för det allmänna sökfältets OCH-semantik, inte för
   ett redan känt namn) när det nämnda vinnamnet har flera ord
   (granskningsfynd efter merge). Precis som samlingslänken kombineras
   länken med samma nollställning av vinlistans sessionsbundna
   filtrering, av samma skäl - annars hade ett redan aktivt filter kunnat
   dölja det nämnda vinet. Matchningen är exakt (skiftlägesokänslig,
   ordgränsmedveten, apostroftolerant - en typografisk och en rak apostrof
   behandlas som samma tecken vid själva JÄMFÖRELSEN, granskningsfynd
   efter merge, eftersom en LLM-genererad svarstext ofta skriver
   apostrofer annorlunda än vad databasen råkar lagra; den TEXT som
   faktiskt visas i länken förblir alltid assistentens ordagranna
   formulering). Den avgörs mot det redan tolkade syntaxträdet för
   svarets markdown, inte genom att leta efter textmönster i den råa
   källtexten före tolkning - en sådan strängersättning hade riskerat att
   träffa text som redan låg inuti en befintlig länk eller ett kodstycke
   i svaret och förstöra dem. Genom att matcha mot det tolkade trädet,
   och medvetet inte gå in i innehållet av en redan befintlig länk,
   skyddas sådan text automatiskt - ett vinnamn som redan är en del av en
   länk eller ett kodstycke länkas alltså inte om.

   Vid en textuell överlappning (t.ex. ett kortare vinnamn som råkar vara
   en delsträng av ett längre) vinner alltid den längsta, mest specifika
   frasen som faktiskt förekommer i texten.

2. Om svaret nämner minst ett av ägarens viner avslutas det med en samlad
   länk som visar EXAKT de nämnda vinerna i vinlistan, oavsett hur många
   gånger vart och ett nämndes. Den länken bygger på en ny, egen facett i
   sök-/filtreringskriterierna som matchar exakt mot ett vins namn (samma
   OCH-mellan-facetter/ELLER-inom-facetten-princip som övriga facetter,
   se [0006](0006-search-orchestration-in-application-layer.md)) - en
   fritextsökning hade inte räckt, eftersom den kräver att alla sökord
   finns i SAMMA vins sökfält, inte att vart och ett av flera olika viner
   matchar var för sig.

   Länken kombinerar den nya facetten med en explicit signal om att
   rensa vinlistans övriga, sessionsbundna filtrering (se
   [0023](0023-session-scoped-filter-memory.md)) - annars hade ett redan
   aktivt filter kunnat dölja ett eller flera av de nämnda vinerna. Den
   signalen betydde tidigare "nollställ helt och ignorera allt annat i
   samma anrop", vilket inte längre stämmer: en nollställning kombinerad
   med en egen, explicit parameter i samma anrop tillämpar numera den
   parametern ovanpå nollställningen, i stället för att bara nollställa
   och strunta i resten. De befintliga länkarna som bara nollställer,
   utan någon egen parameter, beter sig oförändrat.

Länkningen är avsiktligt avgränsad till vinets namn - inte producent,
årgång eller någon kombination av flera fält. Namnet är det fält
assistentens svar naturligt refererar till, och en snävare, säkrare
första version är att föredra framför att försöka gissa vilken
kombination av fält som avsågs.

Den samlade länken rensar bara den del av filtreringen som redan var
sessionsbunden (se [0023](0023-session-scoped-filter-memory.md)) -
tröskelvärdet för antal flaskor är uttryckligen INTE en del av det
minnet (ett separat, kontobundet standardvärde, se README:s
"Filtrering, sökning och sortering") och rörs alltså inte, precis som
de befintliga "Rensa filter"-länkarna redan lämnar det orört. Ett vin
som nämns i svaret men har färre flaskor än användarens sparade
tröskelvärde (t.ex. en redan utdrucken flaska assistenten ändå
diskuterar) kan därför fortfarande vara osynligt i resultatet, precis
som det redan är i den vanliga vinlistevyn - en medveten konsekvens av
att behandla de två mekanismerna lika, inte ett särfall för den här
länken.

## Consequences

- Ett vinnamn som nämns i ett assistentsvar går att klicka sig vidare
  från direkt, utan att själv behöva byta sida och skriva in det i
  sökfältet.
- Matchningen är strikt - ett vin vars namn inte förekommer EXAKT (bortsett
  från skiftläge) i svaret länkas inte, även om det uppenbart är det
  menade vinet (t.ex. en förkortad eller omskriven variant av namnet).
  En medveten avvägning: en säker, förutsägbar matchning väger tyngre än
  att fånga fler, mer diffusa förekomster.
- Den nya facetten känner bara igen exakta namn, inte producent eller
  årgång - två viner med samma namn men olika producent/årgång kan inte
  skiljas åt av vare sig den enskilda eller den samlade länken (båda
  använder samma facett, se punkt 1/2 ovan). En eventuell utökning till
  fler fält är ett separat, framtida beslut.
- Eftersom både den enskilda och den samlade länken använder samma
  exakta facett visas de alltid som samma slags chip i verktygsraden
  (utan "+"-uppdelning) - till skillnad från det allmänna sökfältets
  chip, som medvetet ser annorlunda ut eftersom det representerar en
  annan, bredare sökning.
- Den sessionsbundna filtreringens nollställningssignal betyder numera
  "utgå från standardvärdena för vinlistan" snarare än "nollställ och
  ignorera allt annat i requesten" - en skärpning av
  [0023](0023-session-scoped-filter-memory.md), inte en omprövning av det
  beslutet.
