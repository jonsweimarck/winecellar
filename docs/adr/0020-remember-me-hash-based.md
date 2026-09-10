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

Motivering: appens skala (inte en tjänst med krav på
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
- **Begränsning upptäckt vid kodgranskning, löst i två steg (WINE-43).**
  Webbläsarens säkra cookie-flagga sätts av ramverket bara när appen
  själv uppfattar anropet som krypterat. Driftmiljön terminerar TLS i
  en framförliggande proxy, bekräftat av driftplattformens egen
  dokumentation - appen är därför konfigurerad att lita på proxyns
  signal om det ursprungliga protokollet. Ett automatiskt test skrivet
  i samma story visade dock att den konfigurationen bara löste hälften
  av problemet i ett första steg: den säkra flaggan sattes korrekt på
  själva remember-me-cookien (30 dagars livslängd gör konsekvensen av
  ett uteblivet skydd värre där än för en vanlig session, vilket var
  den ursprungliga oron), men INTE på den vanliga inloggningssessionens
  egen cookie - den skrivs av en lägre nivå i servletcontainern som
  inte ser samma signal.
- **Andra steget (samma story, efter eskalering till arkitekt och
  produktägare):** sessionscookien tvingas nu säker explicit, men bara
  i en produktionsprofil - en generell inställning hade gjort lokal
  utveckling över vanlig HTTP obrukbar (en webbläsare skickar aldrig en
  säkert flaggad cookie tillbaka över en osäker anslutning, så
  inloggningen hade sett ut att fungera men aldrig hållit i sig).
  Produktionsprofilen måste aktiveras uttryckligen av driftmiljön -
  den slår inte på sig själv bara för att appen körs där. Se
  CLAUDE.md, Kända fällor, för exakt vilken miljövariabel som krävs.
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
  appens skala och avsaknad av känsliga data - samma typ av avvägning
  som remember-me-lägesvalet ovan - och alltså ett medvetet accepterat,
  dokumenterat val, inte en brist som ska åtgärdas senare.
- **En andra, separat accepterad risk upptäckt vid en uppföljande
  kodgranskning (samma story, WINE-43).** Samma ramverksmekanism som
  läser signalen om det ursprungliga protokollet (föregående punkt)
  läser även motsvarande vidarebefordrade signaler för värdnamn, port
  och sökvägsprefix - utan någon källbegränsning där heller. I teorin
  skulle en förfalskad sådan signal kunna påverka vilken sida en
  användare skickas till direkt efter en lyckad inloggning. Det här
  täcks INTE av föregående punkts resonemang (som gäller specifikt
  cookiens säkra flagga och webbläsarens skydd mot att ta emot den över
  en osäker anslutning) och behöver därför bedömas separat. Den
  praktiska skadan bedöms ändå som lägre än den föregående risken,
  eftersom den förutsätter att en angripare kontrollerar headrarna i
  offrets egen förfrågan - inte bara att offret klickar en tillskickad
  länk, som ett klassiskt öppet omdirigeringsproblem annars hade
  krävt. Medvetet accepterat av samma skäl som ovan: en rimlig
  avvägning för appens skala och avsaknad av känsliga data, inte en
  brist som ska åtgärdas senare.
