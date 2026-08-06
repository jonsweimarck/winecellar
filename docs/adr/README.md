# Architecture Decision Records

Den här mappen samlar de arkitektur- och designbeslut som formar
`winecellar` men som inte hör hemma i huvud-`README.md` (som beskriver
nuläget, inte historiken bakom det). Varje ADR fångar **ett** beslut:
vilket problem det löste, vad som valdes, vilka alternativ som
övervägdes och vad valet kostar/ger. `../devlog.md` har en mer
detaljerad, kronologisk logg (inklusive CSS-finjustering, enskilda
buggfixar och annat som inte är arkitekturellt) - ADR:erna här är ett
kurerat urval av det som faktiskt påverkar hur projektet är byggt.

## Format

Varje post följer samma enkla mall:

- **Status** - Accepted, Superseded (med länk till efterträdaren) eller
  Deprecated.
- **Context** - vilket problem eller vilken avvägning som stod inför.
- **Decision** - vad som beslutades.
- **Consequences** - vad beslutet faktiskt kostar/ger, inklusive
  avstådda alternativ.

**Undvik rena programreferenser** (citerade metod-/klassnamn, exakta
felmeddelanden, kod-/SQL-snuttar, filsökvägar) - beskriv resonemanget
och avvägningen i ord istället för att peka ut en specifik rad kod som
bevis. Arkitektur-/domänvokabulär som är själva sakfrågan i beslutet
(t.ex. "en beräknad kolumn i databasen", "en ägarreferens per rad") är
inte samma sak och får finnas kvar - det är skillnaden mellan att
beskriva VAD som beslutades och att dokumentera HUR koden råkar se ut
just nu (det senare hör hemma i `../devlog.md` eller i koden själv, se
WINE-36).

## Register

| ADR | Titel | Status |
|---|---|---|
| [0001](0001-thin-domain-layer.md) | Tunt domänlager - ingen roombooking-stil skyddslogik | Accepted |
| [0002](0002-responsive-list-dual-layout.md) | Responsiv vinlista via två layouter bakom en CSS-brytpunkt | Accepted |
| [0003](0003-wine-builder-pattern.md) | Domänobjekt byggs med ett namngivet konstruktionsmönster, inte en positionell konstruktor | Accepted |
| [0004](0004-images-in-bytea.md) | Bilder lagras i databasen, inte i extern objektlagring | Accepted |
| [0005](0005-only-name-required.md) | Bara vinets namn är obligatoriskt | Superseded av [0016](0016-quantity-also-mandatory.md) |
| [0006](0006-search-orchestration-in-application-layer.md) | Filtrering/sökning/sortering orkestreras i applikationslagret, inte i controllern | Accepted |
| [0007](0007-fulltext-search-tsvector.md) | Fritextsökning via en beräknad sökkolumn i databasen | Accepted |
| [0008](0008-filter-chips-plain-links.md) | Filterchips är vanliga länkar, inte dynamiskt uppdaterande | Accepted |
| [0009](0009-whole-app-http-basic-auth.md) | Hela appen bakom inloggning, med ett delat läsbehörighetskonto | Superseded av [0013](0013-multi-user-accounts.md) |
| [0010](0010-excel-tool-standalone-module.md) | Excel-import/export som ett fristående, separat verktyg | Superseded av [0014](0014-web-based-excel-import-export.md) |
| [0011](0011-excel-image-roundtrip-dual-mechanism.md) | Excel-bildrundtripp via två oberoende mekanismer | Deprecated (WINE-32) |
| [0012](0012-label-scanning-llm-integration.md) | Etikettskanning via en extern LLM-tjänst, port/adapter + direkt REST-anrop | Accepted |
| [0013](0013-multi-user-accounts.md) | Flera användare med egna, privata vinlistor | Accepted |
| [0014](0014-web-based-excel-import-export.md) | Webbaserad Excel-import/export, scopead till inloggad användare | Accepted |
| [0015](0015-bulk-import-images-lossy-jpeg.md) | Bulkimportens bilder skalas ned och komprimeras - ingen bit-exakt rundtripp | Accepted |
| [0016](0016-quantity-also-mandatory.md) | Antal flaskor blir obligatoriskt, precis som namnet | Accepted |
| [0017](0017-login-triggered-temp-import-cleanup.md) | Övergivna temp-importmappar städas vid inloggning, inte via ett schema | Accepted |
