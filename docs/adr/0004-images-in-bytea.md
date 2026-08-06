# 0004: Bilder lagras i databasen, inte i extern objektlagring

## Status

Accepted (2026-07-17)

## Context

Varje vin kan ha en etikettbild. Alternativen var att lagra bilddata
direkt i databasen eller i extern objektlagring (t.ex. en
S3-liknande tjänst) med bara en referens i databasen.

## Decision

Bilder lagras direkt tillsammans med resten av vinets data, inte i
extern objektlagring. Motiverat av samlingens storleksordning: en enda
datakälla, enklare backup (ingen extra tjänst att hålla synkad), ingen
ytterligare molntjänst att administrera eller betala för. Bildens
filtyp lagras tillsammans med bilddatan och används oförändrad när
bilden visas upp igen - utan att filtypen stämmer visar webbläsaren
inte bilden trots att bytes finns lagrade.

## Consequences

- Enklare drift: en backup av databasen är en backup av allt,
  inklusive bilder.
- Databasens storlek växer med bildmängden - om samlingen och
  bildvolymen växer kraftigt är en migrering till objektlagring en
  isolerad, senare åtgärd (flytta bara bilddatan), inte något som
  byggs beredskap för nu.
- Vinlistan bäddar aldrig in bilddata i själva sidfragmentet - bilden
  hämtas via en egen, separat förfrågan, så listrenderingen förblir
  lätt även när viner har bilder.
- **Fälla att komma ihåg för framtida binärdatafält:** en naiv,
  standardmässig mappning av ett binärt fält mot databasen gav inte
  den lagringstyp som förväntades - upptäcktes först genom att
  inspektera den faktiska databasstrukturen, inte via applikationens
  beteende (bilden fungerade ändå, trots fel lagringstyp under huven).
  Kontrollera lagringstypen explicit för framtida binärdatafält, lita
  inte bara på att applikationsbeteendet ser rätt ut.
