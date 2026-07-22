# Architecture Decision Records

Den här mappen samlar de arkitektur- och designbeslut som formar
`winecellar` men som inte hör hemma i huvud-`README.md` (som beskriver
nuläget, inte historiken bakom det). Varje ADR fångar **ett** beslut:
vilket problem det löste, vad som valdes, vilka alternativ som
övervägdes och vad valet kostar/ger. `CLAUDE.md` har en mer detaljerad,
kronologisk logg (inklusive CSS-finjustering, enskilda buggfixar och
annat som inte är arkitekturellt) - ADR:erna här är ett kurerat urval av
det som faktiskt påverkar hur projektet är byggt.

## Format

Varje post följer samma enkla mall:

- **Status** - Accepted, Superseded (med länk till efterträdaren) eller
  Deprecated.
- **Context** - vilket problem eller vilken avvägning som stod inför.
- **Decision** - vad som beslutades.
- **Consequences** - vad beslutet faktiskt kostar/ger, inklusive
  avstådda alternativ.

## Register

| ADR | Titel | Status |
|---|---|---|
| [0001](0001-thin-domain-layer.md) | Tunt domänlager - ingen roombooking-stil skyddslogik | Accepted |
| [0002](0002-responsive-list-dual-layout.md) | Responsiv vinlista via två layouter bakom en CSS-brytpunkt | Accepted |
| [0003](0003-wine-builder-pattern.md) | `Wine` byggs med Builder-mönster, inte positionell konstruktor | Accepted |
| [0004](0004-images-in-bytea.md) | Bilder lagras som `bytea` i Postgres, inte i objektlagring | Accepted |
| [0005](0005-only-name-required.md) | Bara vinets namn är obligatoriskt | Accepted |
| [0006](0006-search-orchestration-in-application-layer.md) | Filtrering/sökning/sortering orkestreras i `WineService`, inte i controllern | Accepted |
| [0007](0007-fulltext-search-tsvector.md) | Fritextsökning via en genererad `tsvector`-kolumn i Postgres | Accepted |
| [0008](0008-filter-chips-plain-links.md) | Filterchips är vanliga länkar, inte htmx | Accepted |
| [0009](0009-whole-app-http-basic-auth.md) | Hela appen bakom HTTP Basic, med ett delat läsbehörighetskonto | Accepted |
| [0010](0010-excel-tool-standalone-module.md) | Excel-import/export som en fristående Maven-modul | Accepted |
| [0011](0011-excel-image-roundtrip-dual-mechanism.md) | Excel-bildrundtripp via två oberoende mekanismer | Accepted |
