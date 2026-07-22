# 0009: Hela appen bakom HTTP Basic, med ett delat läsbehörighetskonto

## Status

Accepted (2026-07-12, readonly-kontot tillagt 2026-07-19)

## Context

`roombooking` skyddade bara `/admin/**` och hade ett legitimt anonymt
läsläge. `winecellar` har ingen separat publik läsvy - varje route låter
i grunden en besökare ändra vinsamlingen, och appen var redan nåbar
från det öppna nätet innan detta beslut togs.

Ett behov uppstod senare av att kunna dela en "titta men inte
ändra"-åtkomst utan att lämna ut adminlösenordet.

## Decision

`SecurityConfig` kräver autentisering på **allt** (`.anyRequest().
authenticated()` som fallback), inte bara en admin-del. Två konton:

- `admin`, lösenord från `WINECELLAR_ADMIN_PASSWORD` (miljövariabel i
  produktion, `admin` bara som lokal default) - full åtkomst.
- `readonly`/`readonly`, **hårdkodat** i `SecurityConfig` (inte en
  miljövariabel) eftersom kontot medvetet är tänkt att vara ett känt,
  delbart konto - inte en hemlighet. Får GET `/` och GET
  `/wines/{id}/bild`, nekas allt annat (formulärsidornas GET-routes
  inkluderat, inte bara POST/DELETE - annars går det att komma åt
  "lägg till"/"redigera" genom att gissa på URL:en även om länken är
  dold i UI:t).

CSRF är avstängt globalt - htmx-formulären skickar ingen CSRF-token, och
autentiseringen är stateless Basic-auth per anrop, inte en inloggad
session CSRF-skyddet är till för.

## Consequences

- Ingen legitim anonym åtkomst finns eller behövs - varje avvikelse från
  "kräv autentisering" måste motiveras explicit.
- `WineController` sätter en `kanRedigera`-modellattribut som mallen
  använder för att dölja adminfunktioner för READONLY - bara ett
  UI-lager; den faktiska åtkomstkontrollen sitter i `SecurityConfig`
  och gäller oavsett vad UI:t visar.
- `@WebMvcTest`-slice-tester ser inte `SecurityConfig` automatiskt -
  varje ny testklass måste importera den explicit
  (`@Import(SecurityConfig.class)`), annars slår Spring Boots egen
  slumpade standardsäkerhet in istället och redan gröna tester börjar
  få 401.
- Clever Cloud injicerar apparens miljövariabler även i byggsteget -
  `@WebMvcTest`-klasser som hårdkodar testinloggning måste pinna
  lösenordet explicit (`@TestPropertySource`) för att inte plocka upp
  det riktiga produktionslösenordet under bygget.
