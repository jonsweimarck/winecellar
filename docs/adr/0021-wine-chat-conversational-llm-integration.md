# 0021: AI-chatt om vinsamlingen - persisterad konversationsdomän och en andra, flerturs-formad LLM-integration

## Status

Accepted (2026-09-12)

## Context

WINE-5/[0012](0012-label-scanning-llm-integration.md) gav appen sitt
första beroende av en extern språkmodell, men i en enda, smal form:
en bild in, fem strukturerade fält ut, inget minne mellan anrop. Nu
efterfrågas något arkitektoniskt annorlunda - att fritt kunna chatta
med en assistent om hela sin vinsamling, över flera meddelanden och
flera separata konversationer, med historiken bevarad mellan besök.

## Decision

**En egen, persisterad konversationsdomän, inte en utökning av den
befintliga vintjänsten.** En konversation och dess meddelanden är en
egen sorts data med sin egen livscykel (skapas, växer, kan raderas,
har en övre gräns) - skild från och utan koppling till ett enskilt
vin. Ägarskap scopas per användare på samma sätt som vinsamlingen
redan gör.

**En ny port för själva språkmodellsanropet, separat från den
befintliga etikettolkningsporten - inte en gemensam abstraktion över
båda.** De två integrationerna har fundamentalt olika form: den ena är
ett enstaka anrop utan minne som returnerar fem fasta fält, den andra
är en löpande konversation som skickar med hela sin egen historik vid
varje tur. Att tvinga fram ett gemensamt gränssnitt för det hade gett
mer ceremoni än nytta (samma linje som
[0001](0001-thin-domain-layer.md)s "ingen abstraktion utan ett
konkret skäl"). Vad de DELAR - vilken extern tjänst, autentisering,
modellval, sättet ett HTTP-anrop görs och tolkas på - återanvänds rakt
av, samma konfiguration och samma "direkt REST-anrop, inget
klientbibliotek"-linje som redan gäller.

**Hela den aktuella vinlistan skickas med som kontext vid varje
meddelande, hämtad färsk - ingen selektiv hämtning eller sökbaserad
kontext.** Användaren ska kunna fråga fritt om vad som helst i
samlingen, inte bara det som råkar vara synligt/filtrerat i en
vinlistevy just då. Ett mer sofistikerat urval (t.ex. baserat på vad
frågan handlar om) hade varit overengineering för en samlings storlek
den här appen är byggd för - samma avvägning som redan gäller för
andra delar av domänen (t.ex. varför härkomstträdet beräknas fräscht
istället för att cachas eller normaliseras bort).

**Hela konversationshistoriken skickas med vid varje nytt meddelande -
själva den externa tjänsten är stateless mellan anrop, minnet finns
bara i appens egen lagring.** Det är den externa tjänstens vanliga
samtalsmodell och kräver ingen egen sessionshantering hos
leverantören.

**Assistenten är strikt läsande - den kan aldrig ändra en användares
vinsamling, varken direkt eller via ett bekräftelsesteg.** Att låta
den föreslå eller utföra ändringar hade krävt verktygsanrop och en
betydligt större design- och säkerhetsyta (vad får ändras, hur
bekräftas det, vad händer vid en felaktig tolkning) - avstått för den
här första versionen, inte uteslutet för alltid.

**Fasta, miljövariabelkonfigurerade gränser per användare** - max
antal samtidiga konversationer och max antal meddelanden per
konversation. Att nå en gräns blockerar med ett tydligt felmeddelande;
systemet trimmar aldrig bort gammalt innehåll åt användaren. En
konversation kan bara försvinna genom att användaren själv raderar
den. Motivering: dels en kostnadsspärr (varje meddelande är ett
betalt externt anrop, med hela vinlistan och hela historiken
medskickad), dels att tyst borttagning av en användares egna
konversationer/meddelanden hade varit ett oväntat databortfall - att
kräva ett aktivt val (radera) är mer förutsägbart, även om det
innebär att användaren ibland själv måste städa för att komma vidare.

## Consequences

- En ny, växande datamängd per användare (konversationer och
  meddelanden) med en annan livscykel än resten av domänen - den enda
  tabellen som en användare själv kan tömma helt via radering, till
  skillnad från vinsamlingen.
- Kostnaden per meddelande växer med BÅDE samlingens storlek och
  konversationens längd, eftersom båda skickas i sin helhet vid varje
  tur. Rimligt vid appens nuvarande skala (samma typ av avvägning som
  [0007](0007-fulltext-search-tsvector.md)s härkomstträd), men inte
  obegränsat skalbart - en framtida omprövning (sammanfattning av
  äldre historik, selektiv kontext) är inte utesluten om det blir ett
  verkligt problem.
- Två sinsemellan oberoende LLM-integrationer lever kvar sida vid
  sida, med bara den underliggande anropsmekaniken och
  konfigurationen delad - inte en gemensam tjänstabstraktion. En
  framtida tredje integration av ännu en annan form väntas inte
  automatiskt passa in i någotdera mönstret.
- Vinsamlingen kan bara ändras via de befintliga
  formulärbaserade sidorna - chatten är och förblir enkelriktad tills
  ett eget, medvetet beslut säger annat.
- En användare som når en gräns måste själv radera en konversation
  för att fortsätta - inget automatiskt görs åt dem.
