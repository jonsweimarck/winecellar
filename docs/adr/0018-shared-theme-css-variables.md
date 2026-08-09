# 0018: Gemensamt designsystem i en delad stilmall, byggt på CSS-variabler

## Status

Accepted (2026-08-08)

## Context

Varje sidmall bar tidigare sin egen kompletta uppsättning stilregler
inbäddad i sidhuvudet. Samma knapp-, sidhuvud- och ramdefinitioner fanns
ordagrant kopierade i tre till fyra mallar, och färgvärden var
hårdkodade rakt in i reglerna. Två följder: en visuell ändring behövde
göras på flera ställen för att inte se olika ut beroende på vilken sida
användaren stod på, och ett mörkt läge hade krävt att varje enskild
regel dubblerades.

Målet var ett gränssnitt som känns professionellt och sammanhållet på
både dator och mobil, med ett mörkt läge som användaren kan välja
själv.

## Decision

En delad stilmall levererad som en statisk resurs bär appens
designsystem: färgpalett, typografi och de komponenter som återkommer
över sidor. Sidspecifik layout stannar kvar i respektive mall.

Samtliga färger uttrycks som variabler. Mörkt läge byter bara
variablernas värden, aldrig reglerna som använder dem - en mall får
därför aldrig hårdkoda ett färgvärde, eftersom den då hamnar utanför
temaväxlingen.

Accentfärgen är vinröd, med en ljusare nyans i mörkt läge: den mörka
tonen har för svag kontrast mot en mörk bakgrund. Typsnittet är
operativsystemets eget gränssnittstypsnitt i stället för ett laddat
webbtypsnitt.

Temavalet har tre lägen - följ systemet, ljust, mörkt - och sparas i
webbläsaren, inte per konto i databasen. Valet appliceras av ett litet
skript som körs innan sidan målas första gången.

## Consequences

- **Statiska resurser måste undantas från inloggningskravet.**
  Inloggnings- och registreringssidan renderas för en anonym besökare
  och skulle annars omdirigeras till inloggning även för sin stilmall,
  och därmed visas helt ostylade.
- **Temavalet följer webbläsaren, inte kontot.** Samma användare på en
  annan enhet får sitt systemval i stället för sitt sparade. Accepterat:
  alternativet kräver en kolumn i användartabellen och en rundtripp till
  servern för något som är en ren visningspreferens. Vägen dit är öppen
  om behovet visar sig.
- **Temaskriptet måste ligga i sidhuvudet och köras synkront.** Läggs
  det senare hinner sidan målas i ljust läge och blinka om till mörkt
  vid varje sidladdning.
- Accentfärgen är reserverad för sidans primära handling. Destruktiva
  handlingar tar en dämpad variant - de upprepas en gång per vin i
  listan, och en vägg av accentfärgade knappar drar blicken till just
  det man minst vill råka klicka på.
- Färg får aldrig ensam bära betydelse i det som byggs vidare, eftersom
  paletten byter värden mellan lägena.
- Ingen byggkedja för stilmallar (preprocessor, bundling) införs.
  Variabler räcker för behovet, och webbläsaren löser dem själv.
