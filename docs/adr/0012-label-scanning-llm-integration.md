# 0012: Etikettskanning via Anthropic-API, port/adapter + RestClient

## Status

Accepted (2026-07-24)

## Context

WINE-5 vill låta en användare fotografera en vinetikett och få ett
förifyllt utkast till "lägg till vin"-formuläret, genom att skicka
bilden till en LLM (Anthropic) och be den läsa/härleda `name`,
`producer`, `vintage`, `country` och `region`. Det här är appens
första beroende av en extern tjänst utöver Postgres-tillägget.

## Decision

**Port/adapter, samma mönster som `WineRepository`.** `LabelInterpreter`
(applikationslagret) är en port med en metod, `interpret(byte[], String)
-> Optional<InterpretedLabel>`. `AnthropicLabelInterpreter`
(infrastructure) är den riktiga adaptern. Till skillnad från
`InMemoryWineRepository` (som är en legitim, om än enkel, egen
implementation) finns det ingen meningsfull produktionsanvändning av en
låtsas-LLM - testdubbletten (`FakeLabelInterpreter`) ligger därför bara i
testkoden (`src/test/java/.../acceptance/`), inte i `infrastructure/`.

`LabelInterpretationService` (applikationslagret, en egen tjänst - inte
en metod på `WineService`) orkestrerar: anropar porten, bygger ett
osparat `Wine`-utkast av det som kom tillbaka, och räknar ut vilka av de
fem fälten som faktiskt kunde tolkas (icke-null i svaret). Samma
princip som [0006](0006-search-orchestration-in-application-layer.md)
(orkestrering hör hemma i applikationslagret), tillämpad på en annan
gräns - `WineService` rör bara den redan sparade samlingen
(`WineRepository`), medan etikettolkning inte har någon koppling dit
alls.

**`Optional.empty()` från porten betyder total misslyckad tolkning**
(nätverksfel, LLM-fel, eller en etikett där inget alls kunde
läsas/härledas) - skiljt från ett lyckat svar där enskilda fält råkar
bli `null` (t.ex. bara namnet gick att läsa). `AnthropicLabelInterpreter`
slår ihop dessa fall till `Optional.empty()` om ALLA fem fälten blev
`null`, eftersom det ur användarens perspektiv är samma sak som att
tolkningen misslyckades helt.

**Anrop direkt mot Anthropics Messages API via Spring `RestClient`,
inte den officiella Anthropic Java-SDK:n.** `spring-boot-starter-web`
ger redan `RestClient`/Jackson utan något nytt beroende - samma linje
som [0010](0010-excel-tool-standalone-module.md)s resonemang att hålla
den deployade jaren fri från beroenden som bara behövs för en enda,
smal integrationspunkt. Anthropics REST-API är en enkel POST med en
bild- och textdel i `content`-arrayen; att bädda in en hel SDK för det
vägde inte upp mot en handfull rader manuell JSON-uppbyggnad.

**Konfiguration via miljövariabler**, samma mönster som
`WINECELLAR_ADMIN_PASSWORD`: `WINECELLAR_ANTHROPIC_API_KEY`,
`WINECELLAR_ANTHROPIC_MODEL` (tom/standard-nyckel lokalt - appen startar
ändå, det är bara skanningsanropet som skulle misslyckas utan en riktig
nyckel).

**Prompten instruerar uttryckligen** att `name`/`producer`/`vintage`
ALDRIG får gissas eller härledas (bara läsas rakt av), medan
`country`/`region` FÅR härledas från övrig information på etiketten -
kravet kommer direkt från WINE-5:s "Att göra"-tabell.

**Testning är uppdelad på tre lager**, eftersom scenarierna i WINE-5
faktiskt spänner tre olika sorters beteende:
- Lyckad tolkning (fullständig eller bara namnet) → Cucumber mot
  applikationslagret (`LabelInterpretationService`), med
  `FakeLabelInterpreter` - aldrig ett riktigt API-anrop i tester.
- Misslyckad tolkning → `@WebMvcTest`/MockMvc
  (`WineControllerTest`), eftersom det bara är en
  rendering-utan-krasch-fråga, ingen application-logik att verifiera.
- Att en redigering släcker "tolkat"-markeringen, och att en skannad
  bild faktiskt fyller i och markerar rätt fält i en riktig
  webbläsare → `LabelScanFormIT` (Playwright), eftersom det förra är
  ren klient-JS/DOM-logik utan serveranrop alls - exakt den sortens
  beteende `WineListResponsiveIT` redan finns till för (se
  [0002](0002-responsive-list-dual-layout.md)). `LabelInterpreter`
  (inte `LabelInterpretationService`) mockas här också, av samma skäl
  som ovan.

## Consequences

- Appens första utgående nätverksberoende utöver databasen - ingen
  timeout/retry-hantering byggd utöver vad `RestClient` gör som
  standard; ett misslyckat anrop blir bara `Optional.empty()`
  (`AnthropicLabelInterpreter.interpret` fångar alla undantag).
- Etikettskanning är bara tillgänglig vid TILLÄGG av ett nytt vin
  (`wine.id == null`), inte vid redigering - `POST /wines/tolka-etikett`
  har ingen motsvarighet kopplad till redigeringsformuläret.
- Klientsidans nedskalning (Canvas, före uppladdning) och
  markeringssläckningen (vanlig `input`/`change`-lyssnare) är den
  första mer än triviala JavaScript-koden i projektet - allt annat har
  varit htmx eller en enda rad (filterpanelens stäng-knapp). Inline
  `<script>` i `vin-formular.html`, samma mönster som redan användes
  för den raden, inte en separat statisk JS-fil.
