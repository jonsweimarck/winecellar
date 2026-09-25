# 0023: Vinlistans filtrering minns sig själv för sessionen, inte per konto

## Status

Accepted (2026-09-25)

## Context

Vinlistans sortering, fritextsökning och filter (vintyp, ursprung,
taggar) uttrycks helt i URL:ens queryparametrar - bokmärkbart och
delbart, se [0006](0006-search-orchestration-in-application-layer.md).
Det var ett aktivt val när funktionerna byggdes att en navigering bort
från startsidan (Inställningar, redigera ett vin, ...) och sedan
tillbaka nollställer filtreringen, eftersom en bar `/` (utan
queryparametrar) alltid tolkats som "inga filter". Med antalet
filtreringsmöjligheter som tillkommit sedan dess (flera facetter,
taggar, sortering) känns det inte längre rimligt - en användare som
filtrerat fram en delmängd av sin samling och sedan råkar klicka sig
in på en annan sida tvingas bygga upp samma filtrering på nytt.

Ett par av vinlistans egna länkar bygger redan om samma URL fullt ut
(chipsens borttagningslänkar, se
[0008](0008-filter-chips-plain-links.md)) eller skickar ett fullständigt
formulär vid varje ändring - de behöver inget minne, de UTTRYCKER redan
hela det tänkta tillståndet varje gång. Problemet gäller bara de
requester som inte bär någon filtreringsinformation alls: en redirect
efter tillägg/redigering/borttagning (som alltid går till en helt bar
`/`), en klickad "Avbryt"-länk, eller en helt vanlig sidladdning.

`minQuantity` (antal flaskor minst) löste ett näraliggande men INTE
identiskt problem redan tidigare: ett kontobundet, databaspersisterat
standardvärde som gäller mellan sessioner och enheter. Det är en
medveten, varaktig inställning en användare sätter i Inställningar -
inte "vad jag råkade filtrera på nyss".

## Decision

Vinlistans övriga filtrering (sökterm, sortering, vintyp, ursprung,
taggar - uttryckligen INTE `minQuantity`, som behåller sitt egna,
oförändrade databasmönster) sparas i HTTP-sessionen, inte i databasen.
Sessionen är rätt livslängd för det här beslutet: texten "tills
användaren aktivt väljer att ändra den" beskriver ett minne för det
pågående besöket, inte en varaktig, enhetsöverskridande inställning som
`minQuantity` är. En session är också redan en etablerad plats för
tillfälligt, per-besök-tillstånd i den här appen (se t.ex. den
temporära importsökvägen, som också bara lever i sessionen).

En request utan någon egen filtreringsparameter alls (en bar `/`)
använder sessionens senast sparade filtrering om en sådan finns, annars
kodens vanliga standardvärden - exakt samma första intryck som idag för
en helt ny session. En enskild explicit queryparameter (t.ex. en delad
länk med bara ett sökord) åsidosätter bara sitt eget fält och blir
själv det nya ihågkomna värdet för just det fältet - samma princip
`minQuantity` redan följer, fast nu applicerad på hela filtreringen.

En request som bär sorteringens båda fält (vilket verktygsradens
formulär och chipsens borttagningslänkar alltid gör, oavsett vilket
enskilt fält som egentligen ändrades) betraktas däremot som en
FULLSTÄNDIG, avsiktlig beskrivning av hela filtreringen och ersätter
hela det ihågkomna tillståndet på en gång, i stället för att slås ihop
fält för fält. Utan den regeln hade det varit omöjligt att avmarkera
den sista kryssrutan i en facett - en tom uppsättning kryssrutor går
inte att skilja från "den här facetten nämns inte alls" i en vanlig
HTML-formulärinskickning, så en naiv fält-för-fält-tolkning hade tyst
återställt en borttagen markering ur minnet igen.

De två "Rensa filter"/"Rensa sökning och filter"-länkarna pekade
tidigare på en helt bar `/` - vilket med det här beslutet bara hade
returnerat exakt samma ihågkomna filtrering de skulle ta bort. De fick
därför en egen, explicit signal (en queryparameter) som talar om att
sessionens minne ska tömmas helt, inte bara läsas.

## Consequences

- En användare kan navigera fritt mellan vinlistan och andra sidor
  utan att förlora en pågående filtrering/sökning/sortering - även efter
  att ha lagt till, redigerat eller tagit bort ett vin (vilket alltid
  redirectar till en bar `/`).
- Filtreringen återställs till vanliga standardvärden vid en ny session
  (till exempel en ny inloggning) - medvetet inte kontobunden eller
  enhetsöverskridande, till skillnad från `minQuantity`.
- De två "rensa"-länkarna bär numera en egen signal för att skilja
  "töm minnet" från "läs minnet" - en bar `/` och en `/` med den
  signalen beter sig olika trots att ingen av dem bär någon egen
  filtreringsparameter.
- Ingen ny databastabell eller kolumn - sessionen är redan en befintlig,
  inbyggd mekanism, och tillståndet är uttryckligen inte tänkt att
  överleva den.
