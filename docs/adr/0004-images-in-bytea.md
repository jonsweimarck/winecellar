# 0004: Bilder lagras som `bytea` i Postgres, inte i objektlagring

## Status

Accepted (2026-07-17)

## Context

Varje vin kan ha en etikettbild. Alternativen var att lagra bilddata
direkt i databasen eller i extern objektlagring (t.ex. S3-liknande
tjänst) med bara en referens i `wines`-tabellen.

## Decision

Bilder lagras direkt i `wines`-tabellen (`image bytea`,
`image_mime_type text`), inte i extern objektlagring. Motiverat av
samlingens storleksordning: en enda datakälla, enklare backup (ingen
extra system att synka), ingen ytterligare molntjänst att administrera
eller betala för.

`image_mime_type` sätts från den uppladdade filens `Content-Type` och
används oförändrat som svarets `Content-Type`-header vid visning -
utan matchande MIME-typ visar webbläsaren inte bilden trots att bytes
finns i databasen.

## Consequences

- Enklare drift: en backup av databasen är en backup av allt, inklusive
  bilder.
- Databasens storlek växer med bildmängden - om samlingen och
  bildvolymen växer kraftigt är en migrering till objektlagring en
  isolerad, senare åtgärd (flytta bara bilddatan), inte något som
  byggs beredskap för nu.
- Vinlistan bäddar aldrig in bilddata i själva HTML-fragmentet -
  `<img>` pekar mot en egen `GET /wines/{id}/bild`-route, så
  listrenderingen förblir lätt även när viner har bilder.
- **Fälla att komma ihåg för framtida `byte[]`-fält:** Hibernates
  standardmappning av `@Lob private byte[]` mot Postgres ger en `oid`
  (large object), inte en riktig `bytea`-kolumn - upptäcktes först via
  `\d wines`, inte via HTTP-beteende (bytes stämmer även via `oid`).
  Fixat genom att byta till `@JdbcTypeCode(SqlTypes.VARBINARY)`, med en
  engångsmigrering (`db/migrations/2026-07-17-image-oid-to-bytea.sql`)
  för redan existerande data. Kontrollera kolumntypen explicit för
  framtida `byte[]`-fält, lita inte bara på att applikationsbeteendet
  ser rätt ut.
