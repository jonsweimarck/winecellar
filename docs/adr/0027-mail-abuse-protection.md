# 0027: Skydd mot mailmissbruk - globala tak och honeypot, inget CAPTCHA eller IP-gräns

## Status

Accepted (2026-10-17, WINE-65). Bygger på [0026](0026-email-username-verification-and-password-reset.md),
vars beslut (inklusive att en anonym part kan förbruka en enskild adress mailkvot, att registrering
avslöjar en redan verifierad adress och att svarstider kan skilja) gäller oförändrade.

## Context

Registreringen är öppen och skapar ett konto och skickar ett mail per ny adress. Kvoten per adress
(några få mail per timme) stoppar inte den som registrerar många olika, påhittade adresser. Konsekvenserna
är att SMTP-leverantörens dagskvot (gratisnivån har en låg gräns) förbrukas, att avsändarens rykte skadas
och att användartabellen fylls av skräp. Samtidigt får skyddet inte göra "glömt lösenord" obrukbart för
befintliga användare.

## Decision

- **Globala tak för utgående mail, per timme och per dygn**, räknade i minnet (nollställs vid
  omstart, passar enkelinstansdrift) och satta via miljövariabler med försiktiga standardvärden. Är taket
  nått skapas inget konto och inget mail skickas, men svaret är exakt detsamma som för en lyckad
  registrering, så skyddet avslöjar ingenting.
- **Två separata budgetar:** en gemensam för registrering och "skicka ny länk", och en egen, mindre för
  "glömt lösenord". Uppdelningen gör att ett fullt registreringstak aldrig kan svälta lösenordsåterställning.
  Restrisk (accepterad): den som tömmer återställningsbudgeten kan blockera återställning en stund för alla,
  men det kräver många begäranden mot VERIFIERADE adresser - okända och overifierade adresser förbrukar
  ingenting - och varje adress tillåter bara tre per timme.
- **Honeypot:** registreringsformuläret har ett dolt fält som en människa aldrig fyller i. Är det
  ifyllt skapas inget konto och inget mail skickas, men svaret är detsamma som för en vanlig registrering.
  Träffen loggas utan adress. Fältet har ett namn som webbläsares autofyll inte brukar fylla i.
- **Platserna tas atomärt och återlämnas vid fel.** En plats tas innan något ändras, så samtidiga anrop
  aldrig kan släppa igenom fler mail än taket. Kommer ingen mailförsändelse till stånd (t.ex. vid ett
  databasfel) lämnas platserna tillbaka. Misslyckas själva utskicket (som sker asynkront) återbetalas
  användarens plats per adress, men det globala taket återbetalas INTE: det räknar försöket.
- **Taken är konfigurerbara men begränsade:** timtaket klipps så att det aldrig kan närma sig storleksgränsen
  på kartan över adresser, annars skulle en full karta tyst kunna neka nya adresser.

## Consequences

- Skälet till att det globala taket inte återbetalas vid ett misslyckat utskick: en nedlagd SMTP-server ska
  inte kunna hamras obegränsat, och ett tak som återbetalas vid fel begränsar ingenting just när något är fel.
  Priset är att en längre SMTP-störning kan tömma
  budgeten på mail som aldrig gick iväg, så att nya registreringar tillfälligt får det neutrala svaret utan
  mail tills fönstret passerat.
- En angripare kan fortfarande tömma registreringsbudgeten och därmed hindra äkta registreringar en stund; det
  går inte att skilja på dem utan en identitet att räkna mot. Äkta användare får då det neutrala svaret och
  kan försöka igen, och bekräftelsesidan säger det.
- Taken måste ställas in mot leverantörens faktiska kvot. Standardvärdena är försiktiga gissningar.
- Taken nollställs vid omstart. Vid flera instanser behöver de delas (t.ex. via databasen), som för
  kvoten per adress i 0026.
- **Valda bort:** CAPTCHA (friktion för legitima användare och ett nytt externt beroende, mycket för en liten
  app) och IP-baserad begränsning (en vidarebefordrad klientadress är förfalskningsbar, se
  [0020](0020-remember-me-hash-based.md)). Skulle missbruket bli verkligt kan ett CAPTCHA läggas till senare
  utan att ändra det här beslutet.
- Mailtrafiken kräver STARTTLS när det är påslaget, så att verifierings- och återställningslänkar aldrig går i
  klartext om servern inte erbjuder det. Implicit TLS stöds inte.
