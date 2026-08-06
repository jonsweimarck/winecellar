# 0007: Fritextsökning via en beräknad sökkolumn i databasen

## Status

Accepted (2026-07-21/22)

## Context

Vinlistan behövde fritextsökning över namn, producent, druvor, tasting
notes, Systembolagets beskrivning och Munskänkarnas bedömning. Tre
alternativ övervägdes: enkel delsträngsmatchning, en separat
sök-tabell ("dubbellagring"), och databasens inbyggda fulltextsökning.

Enkel delsträngsmatchning är enkelt men saknar
böjningsform-medvetenhet (t.ex. en sökning på ett ord i singular hittar
inte samma ord i plural). En separat sök-tabell hade krävt egen
synk-logik för att hålla den i fas med huvuddatan.

## Decision

En beräknad sökkolumn ger samma fördel som en separat sök-tabell
(slippa beräkna sökbarheten vid varje fråga) utan att duplicera data -
databasen räknar om kolumnen automatiskt vid varje ändring. Namn,
producent och druvor viktas högre än de längre fritextfälten, så en
träff i namnet/druvorna rankas högre än en träff djupt i en längre
text. Sökkonfigurationen ger böjningsform-medvetenhet (stemming) på
svenska. **Utökad till att även vara okänslig för diakritiska tecken
(WINE-7, 2026-07-24)** - en sökning utan prickar över bokstäverna
hittar ändå ord som har dem, genom att ta bort diakritiska tecken
innan böjningsformsanalysen.

Den beräknade kolumnens definition hålls i ett separat schemaskript
som körs vid varje appstart, eftersom databasens vanliga
migreringsmekanism inte kan skapa den här sortens kolumn eller dess
index. Skriptet tar bort och återskapar kolumnen och dess index varje
gång, eftersom databasen inte tillåter att en beräknad kolumns
definition ändras i efterhand - annars hade en redan existerande
produktionskolumn blivit permanent låst vid sin ursprungliga
definition första gången den skapades.

Den enkla testdubbletten av datalagret implementerar sökningen med en
enklare, skiftlägesokänslig delsträngsmatchning istället för
databasens fulltextsökning - beter sig inte identiskt, men tillräckligt
för acceptanstester som bara bryr sig om vilka viner som matchar.

## Consequences

- Ingen extra tabell att synka - schemat är den enda sanningskällan
  för sökbarheten, och drop-och-återskapa-mönstret innebär att varje
  ändring av sökuttrycket automatiskt konvergerar produktionsdatabasen
  vid nästa omstart, utan en manuell migreringskörning.
- Kostnaden (hela kolumnen och indexet räknas om vid varje appstart)
  är försumbar för samlingsstorleken - inte lämpligt att skala rakt av
  mot en mycket större datamängd utan att ompröva mönstret.
- Det webbläsarbaserade testlagret, som startar en helt ny
  testdatabas per körning, fungerar som en indirekt verifiering av att
  migreringen är korrekt vid varje testkörning, inte bara vid en
  produktionsdeploy.
