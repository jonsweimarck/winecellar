# 0018: Import av dubbletter betraktas som fel, inte som valbara strategier

## Status

Accepted (2026-08-12, WINE-38)

## Context

[0014](0014-web-based-excel-import-export.md) beslutade att webbimporten skulle
ha separata strategier för fullständiga och möjliga dubbletter: öka antal på
befintligt vin, lägg till som nytt, eller hoppa över. Erfarenheten visade att
dessa val mest komplicerade importflödet. Användaren kan redan manuellt lägga
till, redigera eller öka antalet på ett enskilt vin; bulkimporten ska vara en
enkel och förutsägbar överföring av nya rader från en fil.

## Decision

1. **Dubblettstrategierna från ADR 0014 tas bort helt.** Det finns inga val för
   hur fullständiga eller möjliga dubbletter ska hanteras.

2. **En importrad som matchar ett befintligt vin betraktas som ett
   importeringsfel**, oavsett om matchningen är fullständig eller partiell.
   Felet rapporteras i förhandsgranskningen tillsammans med radnummer, på samma
   sätt som rader som saknar namn eller inte kan tolkas.

3. **Endast rader som inte är dubbletter sparas** vid commit-steget. Användaren
   får rätta källfilen och importera på nytt om en dubblett uppstår.

## Consequences

- Importflödet blir enklare för både användare och kod: inga strategier att
  välja, inga beslut att fatta, ingen risk att en vald strategi ger ett
  oväntat resultat.
- Användaren måste aktivt rätta källfilen vid dubbletter, istället för att
  appen gör en automatisk gissning om raden ska slås ihop med eller läggas
  till bredvid ett befintligt vin.
- [0014](0014-web-based-excel-import-export.md)s punkt 4 (gemensam
  dubblettstrategi för hela importen) ersätts av det här beslutet.
- Resultatsammanfattningen efter commit behöver inte längre särskilja rader
  som "fick antalet ökat"; endast sparade nya viner och överhoppade rader
  rapporteras.
- Förhandsgranskningens sammanfattning kan förenklas, eftersom det inte längre
  finns några separata räknare för fullständiga eller möjliga dubbletter.
