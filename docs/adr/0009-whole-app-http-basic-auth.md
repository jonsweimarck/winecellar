# 0009: Hela appen bakom inloggning, med ett delat läsbehörighetskonto

## Status

Superseded av [0013](0013-multi-user-accounts.md) (2026-07-25,
WINE-15) - den ursprungliga inloggningsmodellen och de hårdkodade
kontona är borttagna till förmån för formulärbaserad inloggning med
riktiga, självregistrerade konton. Kvar nedan som historik för varför
den ursprungliga modellen såg ut som den gjorde.

Accepted (2026-07-12, läskontot tillagt 2026-07-19)

## Context

Systerprojektet `roombooking` skyddade bara en administrativ del av
appen och hade ett legitimt anonymt läsläge. `winecellar` har ingen
separat publik läsvy - varje del av appen låter i grunden en besökare
ändra vinsamlingen, och appen var redan nåbar från det öppna nätet
innan detta beslut togs.

Ett behov uppstod senare av att kunna dela en "titta men inte
ändra"-åtkomst utan att lämna ut adminlösenordet.

## Decision

Appen kräver autentisering på allt, inte bara en administrativ del.
Två konton fanns:

- Ett administratörskonto med fullständig åtkomst, med lösenord satt
  via en miljövariabel i produktion.
- Ett läskonto med ett känt, delbart lösenord (medvetet inte hemligt)
  som bara fick titta på listan och se bilder, och nekades allt som
  ändrar data - inklusive formulärsidorna för att lägga till/redigera,
  inte bara själva sparandet, så att en besökare inte kunde komma åt
  dem genom att gissa en webbadress även om länkarna var dolda i
  gränssnittet.

Skydd mot obehöriga tvärsideförfrågningar var avstängt globalt - det
fanns ingen inloggad session av det slag ett sådant skydd är till för
att skydda, bara en autentiseringsmetod som skickar med
användaruppgifterna vid varje enskild förfrågan.

## Consequences

- Ingen legitim anonym åtkomst finns eller behövs - varje avvikelse
  från "kräv autentisering" måste motiveras explicit.
- Gränssnittet döljer adminfunktioner för läskontot - bara ett
  visuellt lager; den faktiska åtkomstkontrollen sitter i
  säkerhetskonfigurationen och gäller oavsett vad gränssnittet visar.
- Webblagrets isolerade tester ser inte säkerhetskonfigurationen
  automatiskt om den inte uttryckligen kopplas in - annars slår en
  annan, oavsiktlig standardsäkerhet in istället och tester som borde
  vara gröna börjar oväntat nekas.
- Driftmiljön injicerar produktionens miljövariabler även i
  byggsteget, inte bara vid körning - tester som hårdkodar
  inloggningsuppgifter måste pinna sina egna testvärden explicit för
  att inte råka plocka upp det riktiga produktionslösenordet under
  bygget.
