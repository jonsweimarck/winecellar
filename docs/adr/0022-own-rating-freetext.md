# 0022: Det egna betyget är fri text, inte längre en sluten skala

## Status

Accepted (2026-09-16)

## Context

Vinet har två separata betygsfält: det egna, personliga betyget en
användare sätter på sitt eget vin, och Munskänkarnas egen bedömning av
samma vin. Båda har hittills delat samma slutna skala med 29 fasta
etiketter, härledda ur källfilens ursprungliga kalkylblad, och lagrats
som en begränsad uppräkningstyp med en motsvarande begränsning i
databasen.

Användare vill kunna skriva sitt eget betyg fritt - en egen kommentar,
en annan skala, eller bara en kort anteckning - snarare än att tvingas
välja ur munskänkarnas fasta lista. Det här gäller uttryckligen bara
det egna betyget. Munskänkarnas bedömning är en extern, refererad källa
snarare än användarens eget omdöme, och det finns inget uttryckt behov
av att kunna avvika från dess etiketter - en sluten skala är fortfarande
rätt modell där.

## Decision

Det egna betyget blir fri text. Den slutna skalan och dess
databasbegränsning tas bort helt för det fältet - vilken text som helst
går att spara, importera och exportera. Munskänkarnas bedömning berörs
inte alls av det här beslutet och behåller sin slutna skala oförändrad.

En användare kan ändå välja att fortsätta fylla i det egna betyget via
munskänkarnas 29 etiketter, som en ren bekvämlighet - en kontobunden
inställning växlar vinformuläret mellan ett fritextfält och en dropdown
med de 29 etiketterna. Även när dropdownen används sparas bara den
valda etikettens text rakt av, aldrig en referens till skalan - samma
lagringsform oavsett hur texten kom till. Detta är en avsiktlig
förenkling: inställningen är en formulärpreferens, inte en del av
datamodellen, och det finns ingen anledning att kunna skilja ett
fritextvärde som råkar vara identiskt med en etikett från ett värde
valt ur dropdownen i efterhand.

Sortering på det egna betyget kan inte längre luta sig mot skalans
inbyggda rangordning, eftersom fältet inte har någon sluten mängd
värden kvar. Fältet behålls ändå som ett sorteringsalternativ - att ta
bort det hade varit en regression för de användare som fortsätter
fylla i det via munskänkarnas etiketter. Sorteringen görs i stället som
en vanlig, skiftlägesokänslig textjämförelse, med exakt samma
riktningssemantik (stigande/fallande) som andra textfält (namn,
producent, land) - ingen specialbehandling. Det är en medveten
avvägning, inte ett försök att bygga en riktig numerisk tolkning av
fritexten - en text som anges med ett inledande siffervärde sorterar
alfabetiskt, inte numeriskt, vilket kan ge missvisande resultat för
vissa kombinationer (t.ex. ett en- och ett tvåsiffrigt betyg), och det
anses vara en rimlig kostnad snarare än något att lösa med en särskild
tolkning av innehållet.

Redan lagrade värden (de korta interna namnen skalan använde internt)
konverteras till sin fullständiga textetikett i en engångsmigrering,
körd före databasbegränsningen tas bort - annars hade redan sparade
betyg plötsligt visats som en teknisk kod i stället för läsbar text.

## Consequences

- Det egna betyget kan innehålla vilken text som helst, av vilken
  längd som helst - inklusive text som råkar likna, men inte exakt
  matchar, en av munskänkarnas etiketter.
- Munskänkarnas bedömning är helt opåverkad - fortsatt en sluten skala
  med samma databasbegränsning som tidigare.
- Import och export av det egna betyget kräver inte längre att texten
  matchar någon av de 29 kända etiketterna - godtycklig text
  rundtrippar oförändrat. Munskänkarnas bedömning kräver fortfarande
  exakt matchning vid import.
- Sorteringen på det egna betyget är en ren alfabetisk textjämförelse,
  utan någon garanti om en numerisk ordning - en känd och accepterad
  begränsning, inte en bugg att fixa i efterhand genom att börja tolka
  fritexten.
- En användares val att fylla i betyget via en dropdown eller fritt är
  reversibelt när som helst och påverkar aldrig redan sparad data -
  bara vilket formulärelement som visas nästa gång vinet redigeras.
