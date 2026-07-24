# 0007: Fritextsökning via en genererad `tsvector`-kolumn i Postgres

## Status

Accepted (2026-07-21/22)

## Context

Vinlistan behövde fritextsökning över namn, producent, druvor, tasting
notes, Systembolagets beskrivning och Munskänkarnas bedömning. Tre
alternativ övervägdes: ren `ILIKE`-matchning, en separat sök-tabell
("dubbellagring"), och Postgres inbyggda fulltextsökning.

`ILIKE` är enkelt men saknar böjningsform-medvetenhet (t.ex. "vin"
hittar inte "viner"). En separat sök-tabell hade krävt synk-logik för
att hålla den i fas med `wines`.

## Decision

En genererad kolumn (`search_vector tsvector`, satt via `schema.sql`,
inte via Hibernate) ger samma fördel som en separat sök-tabell (slippa
beräkna sökbarheten vid varje fråga) utan att duplicera data - Postgres
räknar om kolumnen automatiskt vid varje `INSERT`/`UPDATE`. Namn,
producent och druvor viktas högre (`'A'`) än de längre fritextfälten
(`'B'`), så en träff i namnet/druvorna rankas högre än en träff djupt i
en tasting note. `'swedish'`-textsökningskonfigurationen ger
böjningsform-medvetenhet (stemming). **Uppdaterad till en egen
`'swedish_unaccent'`-konfiguration (WINE-7, 2026-07-24)** - en kopia av
`'swedish'` med `unaccent`-ordboken kedjad före `swedish_stem`, så att
en sökning på "albarino" även hittar druvan "Albariño". Se `schema.sql`
för varför en vanlig `unaccent(text)`-funktion inte kan användas direkt
i den genererade kolumnens uttryck (kräver `IMMUTABLE`, `unaccent()` är
bara `STABLE`) - en namngiven textsökkonfiguration kringgår det kravet,
precis som `'swedish'` redan gjorde.

`schema.sql` kompletterar `ddl-auto: update`, som inte kan skapa en
`GENERATED ALWAYS AS`-kolumn eller ett index. Migreringen körs
automatiskt vid **varje** appstart (`spring.sql.init.mode: always`) och
droppar/återskapar kolumnen och dess index varje gång - eftersom
Postgres inte kan ändra en genererad kolumns uttryck på plats, hade
`ADD COLUMN IF NOT EXISTS` permanent låst en redan existerande
produktionskolumn vid sin ursprungliga definition. `spring.jpa.
defer-datasource-initialization: true` säkerställer att `schema.sql`
körs efter att Hibernate skapat `wines`-tabellen.

`WineRepository.search(String)` implementeras olika i de två adaptrarna:
`JpaWineRepository` mot en riktig `tsvector @@ plainto_tsquery(...)`
med `ts_rank`-baserad sortering, `InMemoryWineRepository` mot en enklare
skiftlägesokänslig delsträngsmatchning (beter sig inte identiskt, men
tillräckligt för acceptanstester som bara bryr sig om vilka viner som
matchar).

## Consequences

- Ingen extra tabell att synka - schemat är den enda sanningskällan för
  sökbarheten, och drop-och-återskapa-mönstret innebär att varje
  ändring av sökuttrycket (t.ex. att lägga till druvor) automatiskt
  konvergerar produktionsdatabasen vid nästa omstart, utan en manuell
  migreringskörning.
- Kostnaden (hela kolumnen och indexet räknas om vid varje appstart) är
  försumbar för samlingsstorleken - inte lämpligt att skala rakt av mot
  en mycket större datamängd utan att ompröva mönstret.
- `WineListResponsiveIT` (som startar en helt ny Testcontainers-Postgres
  per körning) fungerar som en indirekt verifiering av att migreringen
  är korrekt vid varje testkörning, inte bara vid en produktionsdeploy.
