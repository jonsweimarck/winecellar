# 0005: Bara vinets namn är obligatoriskt

## Status

Superseded av [0016](0016-quantity-also-mandatory.md) (2026-07-26) -
antalet flaskor blev sedan också obligatoriskt, se den ADR:n för
motiveringen. Resten av det här beslutet (alla övriga fält förblir
valfria) gäller fortfarande oförändrat.

Accepted (2026-07-22)

## Context

Ursprungligen krävde både webbformuläret och Excel-importen att typ,
producent, land, årgång, plats och antal flaskor var ifyllda för att
spara ett vin. Användaren upplevde detta som ett irritationsmoment -
önskemålet var att snabbt kunna logga ett vin och fylla i resten
senare.

## Decision

Namnet är det enda obligatoriska fältet, både i webbgränssnittet och
vid Excel-import - samma regel på båda ställena. Alla övriga fält
sparas som tomma om de lämnas ifyllda.

## Consequences

- Årgång och antal flaskor gick från att alltid ha ett värde till att
  kunna lämnas tomma - vilket krävde att representationen av dem i
  koden kunde uttrycka "inget värde ännu", till skillnad från fält som
  redan kunde vara tomma sedan tidigare.
- Fält som tidigare implicit alltid antogs vara ifyllda kräver nu
  genomgående null-safe hantering, inklusive vid Excel-import, som en
  gång kraschade när kravet lättades utan att den hanteringen
  uppdaterades i samma steg.
- Gränssnittet behöver explicita skyddsklausuler för varje fält som
  tidigare antogs alltid vara satt (typ, årgång, antal, producent,
  land, plats) - annars kraschar eller renderar sidan fält som tomma
  etiketter.
- Land-/region-/underregionsträdet för filterpanelen måste hoppa över
  viner utan land explicit, eftersom den underliggande datastrukturen
  inte tillåter en tom nyckel.
- Excel-export och -import är fortfarande symmetriska: ett vin sparat
  med bara namnet exporteras och återimporteras korrekt, eftersom
  importlogiken följer samma regel som webbgränssnittet.
