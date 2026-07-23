# 0005: Bara vinets namn är obligatoriskt

## Status

Accepted (2026-07-22)

## Context

Ursprungligen krävde både webbformuläret och Excel-importen att typ,
producent, land, årgång, plats och antal flaskor var ifyllda för att
spara ett vin. Användaren upplevde detta som ett irritationsmoment -
önskemålet var att snabbt kunna logga ett vin och fylla i resten senare.

## Decision

`name` är det enda obligatoriska fältet, både i webb-UI:t
(`WineController`) och vid Excel-import (`WineRowParser`) - samma regel
på båda ställena. Alla övriga fält sparas som `null` om de lämnas tomma.

## Consequences

- `Wine.vintage`/`Wine.quantity` gick från primitiv `int` till nullable
  `Integer` - en primitiv kan inte representera "inget värde ännu", till
  skillnad från de redan nullable `String`-fälten.
- `wine_type`/`country`/`producer`-kolumnerna, som Hibernate/`ddl-auto`
  tidigare implicit behandlat som ifyllda, kräver null-safe hantering
  genomgående - inklusive `ImportExcel`s JDBC-bindning, som en gång
  kraschade med `NullPointerException` när `WineRowParser` lättades utan
  att bindningen uppdaterades i samma steg.
- UI-mallar behöver explicita `th:if`-vakter för varje fält som tidigare
  antogs alltid vara satt (typ, årgång, antal, producent, land, plats)
  - annars kraschar eller renderar sidan fält som tomma etiketter.
- `WineService.härkomstträd()` (filterträdet för land/region/underregion,
  se [0006](0006-search-orchestration-in-application-layer.md)) måste
  hoppa över viner utan land explicit, eftersom `TreeMap` inte tillåter
  en `null`-nyckel.
- Excel-export och -import är fortfarande symmetriska: ett vin sparat
  med bara namnet exporteras och återimporteras korrekt, eftersom
  `WineRowParser` följer samma regel som webb-UI:t.
