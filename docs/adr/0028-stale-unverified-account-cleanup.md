# 0028: Övergivna overifierade konton och utgångna länkar städas vid inloggning

## Status

Accepted (2026-10-17, WINE-65). Följer samma mönster som [0017](0017-login-triggered-temp-import-cleanup.md).

## Context

Sedan [0026](0026-email-username-verification-and-password-reset.md) skapar en registrering ett overifierat
konto, och utgångna verifierings- och återställningslänkar ligger kvar i databasen. Ingen har tidigare städat
dem. Det är inte längre ett problem för att adresser kan "squattas" (omregistrering av en overifierad adress är
bara en ny länk), men skräprader ackumuleras, särskilt vid massregistrering (se
[0027](0027-mail-abuse-protection.md)).

## Decision

- En lyckad inloggning (formulär eller "håll mig inloggad") raderar utgångna länkar och overifierade konton
  som är äldre än sju dagar, tillsammans med kontots länkar (det finns ingen främmande nyckel som gör det
  åt oss).
- Ett gammalt overifierat konto som fortfarande har en giltig länk, eftersom användaren nyss begärt en ny,
  städas inte bort. Raderingen sker villkorat, så att ett konto som verifieras precis då inte raderas.
- Verifierade konton, färska overifierade konton och giltiga länkar rörs aldrig.
- Städningen körs högst en gång per timme, så att inte varje inloggning kostar en omgång raderingar.
- Ett fel i städningen loggas, utan någon tokeninformation, och fäller aldrig inloggningen.
- Ingen schemaläggare införs.

## Consequences

- En app som ingen loggar in i städas inte. Det är försumbart för appens storlek, och samma avvägning som i 0017.
- Städningen körs på inloggningstråden, men begränsningen till en gång per timme håller kostnaden nere även efter
  en våg av skräpregistreringar.
- Tidpunkten för senaste städning hålls i minnet och nollställs vid omstart, vilket bara kan ge en extra
  städomgång.
- Ett konto som raderats på det här sättet försvinner helt; den som registrerar om adressen får ett nytt konto
  och en ny länk.
- **Valda bort:** en schemalagd städning (ny infrastruktur för lite nytta), och att aldrig städa (skräpet växer
  obegränsat vid missbruk).
