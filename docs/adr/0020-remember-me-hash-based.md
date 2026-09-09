# 0020: "Håll mig inloggad" via hash-baserad remember-me, inte persistenta tokens

## Status

Accepted (2026-09-09)

## Context

WINE-40 kompletterar den formulärbaserade inloggningen (se
[0013](0013-multi-user-accounts.md)) med en "håll mig inloggad"-kryssruta,
så en användare slipper logga in på nytt varje gång sessionen går ut
eller webbläsaren stängs. Spring Security har inbyggt stöd för det här
i två varianter:

1. **Hash-baserad** - en signerad cookie som kodar användarnamn,
   utgångstid och en hash byggd av lösenordet plus en delad
   servernyckel. Ingen egen lagring behövs - cookien själv räcker för
   att återautentisera.
2. **Persistent token-baserad** - varje utfärdad cookie motsvaras av en
   egen rad i en databastabell (slumpad token, användare, senast använd).
   Tokens roteras vid varje användning och kan återkallas individuellt
   (t.ex. "logga ut alla enheter" eller en administrativ spärrning av en
   enskild kvarglömd session).

## Decision

Det hash-baserade läget används, med en fast signeringsnyckel satt via
konfiguration (miljövariabel i produktion, samma mönster som appens
övriga externa hemligheter) och en giltighetstid på 30 dagar.

Motivering: appens skala (ett lärprojekt, inte en tjänst med krav på
säkerhetsrevision eller enhetsöversikt) gör inte den extra
databastabellen, städlogiken för utgångna rader och den ytterligare
adapterkoden motiverad. Det hash-baserade läget ger samma
användarupplevelse (en ikryssad ruta räcker för att slippa logga in på
nytt) utan att introducera någon ny lagringsmodell. Den enda praktiska
skillnaden en användare skulle märka är att en enskild kvarglömd
inloggning på en delad/förlorad enhet inte går att återkalla i förväg -
bara ett byte av lösenordet (vilket ändrar den hash cookien bygger på)
eller ett byte av den delade signeringsnyckeln ogiltigförklarar samtliga
utfärdade cookies på en gång, inte en i taget.

## Consequences

- Ingen ny databastabell eller städmekanism - remember-me-cookien är
  helt självbärande.
- En signeringsnyckel måste hållas hemlig och stabil över
  applikationens omstarter (annars ogiltigförklaras alla utestående
  cookies vid varje redeploy) - samma driftsmässiga hänsyn som appens
  övriga miljövariabelbaserade hemligheter.
- Ett lösenordsbyte ogiltigförklarar automatiskt användarens tidigare
  utfärdade remember-me-cookies (hashen bygger på lösenordet) - ett
  önskat säkerhetsbeteende, inte en bieffekt att kompensera för.
- Det finns ingen väg att återkalla en enskild förlorad/delad enhets
  cookie i förväg, bara att byta lösenord (drabbar alla enheter) eller
  rotera den delade nyckeln (drabbar alla användare). Om ett konkret
  behov av enhetsspecifik återkallning uppstår senare är en migrering
  till det persistenta läget ett rimligt nästa steg - inget i det här
  beslutet stänger den vägen.
- **Begränsning upptäckt vid kodgranskning, delvis åtgärdad (WINE-43) -
  en kvarstående, olöst del finns fortfarande kvar.** Webbläsarens
  säkra cookie-flagga sätts av ramverket bara när appen själv uppfattar
  anropet som krypterat. Driftmiljön terminerar TLS i en framförliggande
  proxy, bekräftat av driftplattformens egen dokumentation - appen är
  därför konfigurerad att lita på proxyns signal om det ursprungliga
  protokollet. Ett automatiskt test skrivet i samma story bekräftade
  dock att den konfigurationen bara löser hälften av problemet: den
  säkra flaggan sätts numera korrekt på själva remember-me-cookien
  (30 dagars livslängd gör konsekvensen av ett uteblivet skydd värre
  där än för en vanlig session, vilket var den ursprungliga oron), men
  INTE på den vanliga inloggningssessionens egen cookie - den skrivs av
  en lägre nivå i servletcontainern som inte ser samma signal. Detta var
  odokumenterat och otestat fram till WINE-43. En fullständig lösning
  (t.ex. att explicit tvinga fram en säker sessionscookie) har egna
  avvägningar - framför allt att den skulle göra lokal utveckling över
  vanlig HTTP obrukbar utan särskiljning per miljö - och är därför
  medvetet lämnad som en öppen fråga, inte löst i den här storyn.
- **Medvetet accepterad risk i samma lösning (WINE-43):** att lita på
  proxyns signal om det ursprungliga protokollet innebär att signalen
  litas på från vilken källa som helst, utan någon motsvarande
  begränsning på vilka avsändare som får skicka den - till skillnad
  från det alternativ som hade byggt på servletcontainerns egen,
  käll-adressbegränsade tolkning. Om driftplattformens proxy någon
  gång inte skulle rensa bort en klients egen sådan signal, eller om
  appen någon gång blir nåbar förbi proxyn, skulle en förfalskad signal
  i teorin kunna få den säkra cookie-flaggan att sättas felaktigt.
  Den praktiska skadan är ändå begränsad, eftersom webbläsare enligt
  cookie-specifikationen avvisar en säkert flaggad cookie som tas emot
  över ett faktiskt osäkert svar. Bedömt som en rimlig avvägning för
  ett lärprojekt utan känsliga data - samma typ av avvägning som
  remember-me-lägesvalet ovan - och alltså ett medvetet accepterat,
  dokumenterat val, inte en brist som ska åtgärdas senare.
