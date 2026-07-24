# 0013: Flera användare med egna, privata vinlistor

## Status

Accepted (2026-07-24) - beslutet är taget och nedskrivet innan
implementationen påbörjas, ovanligt för det här projektet (som annars
skriver ADR:er i samband med att koden landar, se `docs/adr/README.md`).
Motiveringen är att omställningen är stor nog att spänna över flera
separata stories (WINE-10 till WINE-17 i YouTrack) - att fastslå
vokabulär och vägval här, före kod, gör att en session som senare
plockar upp en enskild story inte behöver återupptäcka avvägningarna ur
koden.

## Context

Appen har hittills haft en enda delad vinsamling, skyddad av två
hårdkodade konton (`admin`/`readonly`) via HTTP Basic - se
[0009](0009-whole-app-http-basic-auth.md). Ambitionen nu är att flera
personer ska kunna använda appen samtidigt, var och en med sin egen,
fristående vinsamling - inte en delad källare med olika
behörighetsnivåer som idag.

## Decision

1. **Öppen självregistrering.** Vem som helst med länken till appen kan
   skapa ett konto själv. Motiverat av att kombinationen med "helt
   privat per användare" (nedan) håller risken låg - en ny registrering
   ger bara en tom, privat lista, aldrig åtkomst till någon annans data.
   Ingen e-postverifiering eller godkännandeflöde - ett lärprojekt av
   den här storleken behöver inte den ceremonin.

2. **Formulärbaserad inloggning med session, inte HTTP Basic.** Ersätter
   [0009](0009-whole-app-http-basic-auth.md)s mekanism helt. HTTP Basic
   har ingen bra "logga ut"-känsla och webbläsarens inbyggda
   inloggningsruta blir förvirrande med flera namngivna konton.
   Konsekvens: CSRF måste slås på igen (var avstängt i 0009 just för att
   Basic-auth är stateless), och htmx-formulären behöver en CSRF-token
   (meta-tag i layouten + en `htmx:configRequest`-lyssnare som lägger
   till token-headern på varje htmx-request).

3. **Varje användares vinlista är helt privat.** Ingen delning, ingen
   READONLY-roll. Enklaste modellen - en inloggad användare har samma
   rättigheter till sin egen data, punkt, vilket tar bort behovet av
   rollhantering helt. Om delning (t.ex. "visa min lista för en vän
   read-only") blir ett verkligt behov senare är det en ny, separat
   funktion ovanpå den här grunden, inte en ombyggnad av beslutet.

4. **Datamodell: en `User`-entitet, plus en `owner_id`-kolumn på
   `wines`.** Varje vin får exakt en ägare. Ingen delad
   organisationsnivå ovanför den enskilda användaren.

5. **Befintlig produktionsdata (~30 viner) knyts till det första riktiga
   kontot som registreras, inte till ett återskapat "admin"-konto.** En
   engångsmigrering (samma mönster som tidigare engångsmigreringar i
   projektet, se `db/migrations/2026-07-17-image-oid-to-bytea.sql`) körs
   mot produktionsdatabasen efter att kontot är registrerat, se WINE-17.

6. **Import/export blir en webbfunktion, scopead till inloggad
   användare - vilket river upp [0010](0010-excel-tool-standalone-module.md).**
   Apache POI hölls tidigare medvetet utanför den deployade appen
   eftersom Excel-verktyget bara var ett lokalt engångsverktyg för
   utvecklaren. Så fort import/export blir en riktig, användarvänd
   funktion i webbappen är POI som runtime-beroende motiverat på ett
   sätt det inte var innan. Själva upprivningen av 0010 görs som en egen
   ADR när den fasen (Fas 2) påbörjas, inte här - den här punkten
   fastslår bara riktningen.

7. **Bilder vid webbaserad import hanteras via en mappväljare i
   webbläsaren (`<input type="file" webkitdirectory>`), inte via en
   lokal serverfilsökväg.** Dagens verktyg (`Bildmatchare`,
   `WINECELLAR_LOCAL_IMAGE_FOLDER`) kan läsa en mapp på utvecklarens
   egen dator eftersom verktyget körs lokalt - en webbserver har ingen
   sådan åtkomst till en användares dator, oavsett vad som skrivs in i
   ett textfält. En webbläsarbaserad mappväljare löser samma behov:
   webbläsaren läser mappens filer lokalt och skickar med dem som en
   batch i samma uppladdning som xlsx-filen, så att servern kan
   återanvända samma filnamn-mot-vinnamn-matchning som `Bildmatchare`
   redan gör. xlsx-filen förblir ren data - bilder bäddas inte in i
   den för återimport. Detaljer och exakt scope hör hemma i Fas 2:s
   egna stories/ADR - listas här bara som en bekräftad riktning.

## Consequences

- `SecurityConfig` skrivs om i grunden - ingen roll-uppdelning
  (ADMIN/READONLY) kvar, bara `authenticated()` för alla skyddade
  routes.
- `WINECELLAR_ADMIN_PASSWORD` blir överflödig och tas bort - var
  specifik för det gamla, enda admin-kontot.
- CSRF-skydd återinförs globalt - påverkar alla existerande
  htmx-formulär i appen, inte bara nya.
- Varje metod i `WineService`/`WineRepository` (båda adaptrarna,
  `JpaWineRepository` och `InMemoryWineRepository`) måste scopeas till
  den inloggade användaren - en bred, mekanisk men riskfylld ändring
  (en glömd scoping i en enda metod är ett dataläckage mellan
  användare). Ett eget acceptanstest för dataisolering byggs specifikt
  för att fånga just den risken (WINE-14).
- Hela testsviten (Cucumber, `WineControllerTest`, Playwright-testerna)
  måste uppdateras till den nya inloggningsmodellen - se WINE-16/WINE-18.
- [0009](0009-whole-app-http-basic-auth.md) markeras som Superseded av
  den här ADR:n först när implementationen faktiskt landar (WINE-15 tar
  bort de gamla kontona och HTTP Basic-mekanismen) - inte redan nu, då
  koden fram tills dess fortfarande använder HTTP Basic och 0009
  fortfarande beskriver verkligheten korrekt.
- [0010](0010-excel-tool-standalone-module.md) rivs upp separat, i en
  egen ADR, när Fas 2 (import/export via webben) påbörjas - inte en del
  av den här ADR:ns scope.
