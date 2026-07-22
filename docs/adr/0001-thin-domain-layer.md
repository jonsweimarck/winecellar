# 0001: Tunt domänlager - ingen roombooking-stil skyddslogik

## Status

Accepted (2026-07-12)

## Context

`winecellar` följer samma hexagonala lagerindelning som systerprojektet
`roombooking` (`domain/`, `application/`, `infrastructure/`, `web/`).
`roombooking`s domänlager bar på riktiga affärsregler att skydda - bland
annat tidsberoende bokningsregler, vilket motiverade abstraktioner som
en injicerad `Clock` för att göra den logiken testbar och deterministisk.

`winecellar` är i praktiken en CRUD-app: ett vin har ett antal fält, och
det finns ingen regel om *när* eller *under vilka förutsättningar* ett
vin får sparas, ändras eller tas bort.

## Decision

Bygg inte in skyddsmekanismer eller abstraktioner som `roombooking` hade
av domänskäl, om de inte har en motsvarighet här. Domänlagret
(`Wine`, `WineType`, `Rating`) innehåller rena dataobjekt och enum-baserad
validering av slutna värdemängder - inga tidsberoende regler, ingen
`Clock`-injicering, inget separat "policy"-lager.

`WineService` har en enda `save`-metod, inte separata `addWine`/
`updateWine` - eftersom det inte finns någon skillnad i validering eller
sidoeffekt mellan att skapa och uppdatera ett vin, hade två identiskt
implementerade metoder bara varit två namn på samma sak.

## Consequences

- Domänlagret är litet och lätt att överblicka - `Wine` är i praktiken
  en datastruktur med begränsad självvalidering (via `WineType`/`Rating`
  som slutna enum-mängder), inte en bärare av affärsregler.
- Om en verklig affärsregel dyker upp senare (t.ex. att ett vin inte
  ska kunna raderas om det har vissa relationer) är den naturliga platsen
  fortfarande `WineService`/domänlagret - beslutet stänger inte dörren
  för det, det bara undviker att bygga i förväg för regler som inte
  finns än.
- Testfokus ligger istället på UI-lagrets komplexitet (se
  [0002](0002-responsive-list-dual-layout.md)), inte på domänlogik -
  detta är den huvudsakliga arkitektoniska skillnaden mot `roombooking`.
