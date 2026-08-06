# 0001: Tunt domänlager - ingen roombooking-stil skyddslogik

## Status

Accepted (2026-07-12)

## Context

`winecellar` följer samma hexagonala lagerindelning som systerprojektet
`roombooking`. Det projektets domänlager bar på riktiga affärsregler
att skydda - bland annat tidsberoende bokningsregler, vilket
motiverade abstraktioner för att göra den logiken testbar och
deterministisk oavsett när den kördes.

`winecellar` är i praktiken en CRUD-app: ett vin har ett antal fält,
och det finns ingen regel om *när* eller *under vilka förutsättningar*
ett vin får sparas, ändras eller tas bort.

## Decision

Bygg inte in skyddsmekanismer eller abstraktioner motiverade av
affärsregler som inte har en motsvarighet här. Domänlagret innehåller
rena dataobjekt och enum-baserad validering av slutna värdemängder
(vintyp, betyg) - inga tidsberoende regler, ingen tidsabstraktion,
inget separat policylager.

Tjänstelagret har en enda operation för att spara ett vin, inte
separata operationer för att skapa och uppdatera - det finns ingen
skillnad i validering eller sidoeffekt mellan de två, så en uppdelning
hade bara varit två namn på samma sak.

## Consequences

- Domänlagret är litet och lätt att överblicka - vinet är i praktiken
  en datastruktur med begränsad självvalidering, inte en bärare av
  affärsregler.
- Om en verklig affärsregel dyker upp senare (t.ex. att ett vin inte
  ska kunna raderas om det har vissa relationer) är den naturliga
  platsen fortfarande tjänste-/domänlagret - beslutet stänger inte
  dörren för det, det bara undviker att bygga i förväg för regler som
  inte finns än.
- Testfokus ligger istället på UI-lagrets komplexitet (se
  [0002](0002-responsive-list-dual-layout.md)), inte på domänlogik -
  detta är den huvudsakliga arkitektoniska skillnaden mot `roombooking`.
