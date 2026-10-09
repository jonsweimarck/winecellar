# 0026: E-postadress som användarnamn, verifiering via mail och "glömt lösenord"

## Status

Accepted (2026-10-09). Ändrar registrerings- och inloggningsdelen av
[0013](0013-multi-user-accounts.md) (öppen självregistrering med fritt
användarnamn och direkt inloggning efter registrering); resten av 0013
(privata listor, formulärinloggning, ägarscopning) gäller oförändrat.

## Context

Användarnamnet var en fri sträng och det fanns ingen väg tillbaka in i
ett konto om lösenordet glömdes - bara manuell databasändring. Med öppen
självregistrering fanns dessutom ingen koppling mellan ett konto och en
verklig person. Att kunna återställa ett lösenord kräver en pålitlig
kontaktkanal, och den enklaste är en e-postadress som kontot bevisat sig
äga.

## Decision

- **Användarnamnet är en e-postadress.** Valideringen är medvetet lös:
  en lokal del, ett snabel-a och en domändel med minst en punkt, inga
  blanksteg. Ingen RFC-regex - hellre släppa igenom en udda men korrekt
  adress än underkänna en riktig; det slutgiltiga beviset är att mailet
  kommer fram. Adressen trimmas och sparas i gemener, och unikhet
  gäller oavsett versaler (uppslag är skiftlägesokänsliga).
- **Konton skapas overifierade och kan inte logga in förrän adressen
  verifierats** via en engångslänk (giltig 24 timmar). Användaren loggas
  inte in automatiskt efter registrering. Kontrollen av om kontot är
  aktivt sker efter lösenordskontrollen, så att "adressen måste
  verifieras" bara avslöjas för den som kan lösenordet. En ny
  verifieringslänk kan begäras och gör de gamla ogiltiga.
- **Befintliga konton markeras som verifierade av en engångsmigrering**,
  utan mail och utan retroaktiv formatvalidering - gamla användarnamn som
  inte är e-postadresser fortsätter fungera för inloggning, men de kan
  inte använda "glömt lösenord" (ingen adress att mejla).
- **"Glömt lösenord":** en anonym begäran mailar en återställningslänk
  (giltig 1 timme) men bara om adressen tillhör ett verifierat konto.
  Svaret är alltid exakt detsamma oavsett om adressen finns, och
  utskicket görs asynkront så att svarstiden inte avslöjar skillnaden.
  Mailutskick per adress begränsas (några få per timme, i minnet).
  En ny begäran ogiltigförklarar tidigare länkar. Ett lösenordsbyte
  avslutar användarens övriga sessioner, och "håll mig inloggad"-cookien
  upphör att gälla av sig själv eftersom dess signatur bygger på
  lösenordshashen.
- **Tokens:** kryptografiskt slumpmässiga (256 bitar), bara en hash
  lagras (aldrig själva tokenet), jämförelse i konstant tid,
  engångsbruk, och ett tokens slag (verifiering respektive återställning)
  kan inte användas för det andra. Länkarna i mailen öppnar en sida med
  en bekräfta-knapp; det är knappens POST som förbrukar tokenet, så att
  en mailskanner eller länkförhandsvisning som hämtar länken inte
  förbrukar den.
- **Mail via en port** med tre adaptrar: SMTP (konfigurerad med
  miljövariabler), en logg-adapter som inte skickar något när ingen SMTP
  är konfigurerad (appen startar alltid), och en fake i tester. Loggadaptern
  skriver mailets innehåll - och därmed tokenlänken - bara om det uttryckligen
  slås på lokalt; standard är att aldrig logga tokens.
- Tiden hämtas från en injicerbar klocka så att utgångsregler går att
  testa utan att vänta.

## Consequences

- Ett nytt driftbeslut: en SMTP-leverantör måste väljas och sättas i
  produktionsmiljön, annars skickas inga mail och inga nya konton kan
  aktiveras. Utan konfiguration startar appen ändå men loggar en varning.
  Appens publika adress måste också sättas, annars pekar länkarna på
  localhost.
- Begränsningen av mailutskick och sessionsregistret ligger i minnet och
  passar enkelinstansdrift; vid utskalning till flera instanser behöver de
  delas (t.ex. via databasen).
- Svarstidsutjämningen är "så långt rimligt": ett riktigt utskick sker på
  en egen tråd och okända adresser gör motsvarande lätta arbete, men ingen
  strikt konstant tid garanteras.
- Registreringen avslöjar fortfarande att en adress redan är upptagen,
  inklusive via svarstid (krävs för att en användare ska förstå varför
  registreringen avvisas; ingen dummy-hashning görs) - till skillnad från
  "glömt lösenord", som inte gör det.
- Samma sak gäller "skicka ny verifieringslänk": en känd overifierad adress ger en
  databasskrivning och ett mail, en okänd bara en hash, så svarstiden kan skilja. Det är
  medvetet accepterat (samma resonemang som uppräkningen vid registrering) - inget
  dummyarbete läggs till.
- Skydd mot att någon förhandsregistrerar en annans adress: en ny
  registrering för en ännu OVERIFIERAD adress skriver över lösenordet och
  utfärdar ett nytt token (tidigare ogiltigförklaras), och svaret ser ut som
  för en ny adress. Då fungerar inte angriparens lösenord när den riktige
  ägaren registrerat om sig. För en verifierad adress gäller "upptaget".
  Gamla overifierade konton städas inte bort. Överskrivningen och
  ogiltigförklaringen av gamla länkar sker oberoende av mailkvoten (annars
  kunde en angripare tömma kvoten först); bara själva utskicket kvoteras, med en
  egen kvot för omregistrering skild från "skicka ny länk" och glömt lösenord.
  Medvetet val: den som äger brevlådan vinner - ett overifierat konto är
  tomt, så inget går förlorat när lösenordet skrivs över.
- Mailkvoten per adress förbrukas först när ett mail faktiskt ska skickas,
  så okända adresser aldrig hamnar i begränsarens karta. En anonym part kan
  dock förbruka kvoten för en känd adress och därmed tillfälligt hindra
  mail till den (accepterad störning). Kartan är storleksbegränsad, utgångna
  poster rensas och när den är full nekas nya adresser tyst. Ingen
  IP-baserad begränsning görs (en vidarebefordrad klientadress är
  förfalskningsbar, se [0020](0020-remember-me-hash-based.md)).
- Inlösen av ett token är atomär (den som raderar raden vinner), utfärdande
  sker i en transaktion med ett unikt index på användare och slag, så två
  samtidiga anrop kan inte ge två giltiga tokens eller dubbelinlösen.
- Ett verifierat konto vars adress senare slutar fungera kan inte
  återställas utan manuell hjälp; ingen byte-av-adress-funktion byggs nu.
- Alternativ som valdes bort: att låta länken i mailet förbruka tokenet
  direkt vid GET (enklare, men sårbart för mailskannrar), strikt
  RFC-validering (risk att avvisa korrekta adresser), och att skicka ett
  mail till alla befintliga konton för att bekräfta adressen (onödigt
  friktion för redan etablerade användare).
