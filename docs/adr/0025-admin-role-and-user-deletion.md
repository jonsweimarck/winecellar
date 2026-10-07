# 0025: En enkel adminroll som kan förvalta konton, inklusive radera användare

## Status

Accepted (2026-10-07)

## Context

[0013](0013-multi-user-accounts.md) införde öppen självregistrering och
tog uttryckligen bort alla roller - alla användare är jämlika och ser
bara sin egen data. Det lämnade ingen väg att städa bort ett konto (t.ex.
ett testkonto eller en oönskad registrering) utan att gå direkt mot
databasen. Behovet är litet: en eller några få betrodda personer som kan
se vilka konton som finns, utse fler administratörer och radera ett
konto med allt det äger.

## Decision

- En användare kan vara admin, lagrat som en enkel sant/falskt-egenskap
  på kontot. Det är den enda rollen - ingen rollhierarki.
- Ingen blir admin av sig själv eller av en migrering. Nya konton är
  aldrig admin. Det första admin-kontot sätts manuellt direkt i
  databasen av driftansvarig; därefter kan en admin göra andra till
  admin via adminsidan.
- Adminsidan listar alla användarnamn med två åtgärder per rad: göra
  till admin och radera. Sidan nås via ett menyval som bara visas för
  admin, men behörigheten avgörs server-side på två nivåer: webbskyddet
  släpper bara in admin till adminsidans adresser (utloggad skickas till
  inloggning, inloggad icke-admin får förbjuden), och applikationslagret
  kontrollerar själv att den agerande är admin - så en dold knapp är
  aldrig det enda skyddet.
- Radering tar bort användaren och allt hen äger: chattmeddelanden,
  konversationer, viner med taggar, och sist kontot, i en enda
  transaktion. Schemat har ingen kaskadradering, så ordningen styrs
  explicit av koden. Bekräftelse sker i en native dialog, inte
  webbläsarens confirm.
- En admin kan inte radera sig själv (knappen visas inte och servern
  ignorerar försöket utan fel). Det garanterar att det alltid finns
  minst en admin kvar efter en radering.
- En raderad användares inloggning upphör direkt: pågående sessioner
  upphävs vid raderingen, och en "håll mig inloggad"-cookie slutar
  fungera eftersom användaren inte längre finns att slå upp.

## Consequences

- Att göra någon till admin slår igenom först vid dennes nästa
  inloggning, eftersom rättigheterna läses in när sessionen skapas.
  Accepterat som enklast; adminsidan säger det i sitt meddelande.
- Att ta bort admin-rättighet byggs inte nu. Ett felaktigt utsett
  admin-konto måste raderas, eller ändras direkt i databasen.
- Appen håller nu ett register över pågående sessioner i minnet (för
  att kunna upphäva en raderad användares). Det passar en
  enkelinstansdrift; skulle appen skalas ut till flera instanser måste
  registret ersättas av något delat.
- Raderingen är oåterkallelig och omfattar all användarens data. Ingen
  mjuk radering eller ångrafunktion byggs - samma "tunt domänlager"-linje
  som [0001](0001-thin-domain-layer.md).
