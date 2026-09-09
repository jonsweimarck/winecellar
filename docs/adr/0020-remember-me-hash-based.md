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
