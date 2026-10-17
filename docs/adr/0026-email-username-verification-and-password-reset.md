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
- **Registreringen frågar bara efter e-postadressen; lösenordet väljs på
  verifieringslänken.** Ett konto skapas overifierat med ett slumpmässigt,
  oanvändbart lösenord som ingen känner till, och kan inte logga in förrän
  adressen verifierats via en engångslänk (giltig 24 timmar). Länken öppnar
  en sida där användaren väljer och bekräftar sitt lösenord (samma regler som
  vid "glömt lösenord"); först då markeras kontot verifierat. Användaren loggas
  inte in automatiskt, utan skickas till inloggningen. Alla inloggningsfel ger
  samma svar, så att inget avslöjar att ett konto finns men är overifierat;
  inloggningssidan erbjuder i stället alltid en länk för att begära en ny
  verifieringslänk. Omregistrering av en overifierad adress är detsamma som att
  begära en ny länk: den gamla ersätts och en ny mailas, under den gemensamma
  mailkvoten. Tidigare övervägda varianter där ett lösenord anges vid
  registreringen (och binds till kontot eller till tokenet) övergavs, eftersom de
  lämnar kvar ett lösenord som någon annan än brevlådans ägare känner till.
- **Befintliga konton markeras som verifierade av en engångsmigrering**,
  utan mail och utan retroaktiv formatvalidering - gamla användarnamn som
  inte är e-postadresser fortsätter fungera för inloggning, men de kan
  inte använda "glömt lösenord" (ingen adress att mejla).
- **"Glömt lösenord":** en anonym begäran mailar en återställningslänk
  (giltig 1 timme) men bara om adressen tillhör ett verifierat konto.
  Svaret är alltid exakt detsamma oavsett om adressen finns, och
  utskicket görs asynkront så att svarstiden inte avslöjar skillnaden.
  Mailutskick per adress begränsas (några få per timme, i minnet; verifieringsmail har en gemensam kvot för registrering och "skicka ny länk").
  En ny begäran ogiltigförklarar tidigare länkar. Ett lösenordsbyte
  avslutar användarens övriga sessioner, och "håll mig inloggad"-cookien
  upphör att gälla av sig själv eftersom dess signatur bygger på
  lösenordshashen.
- **Tokens:** kryptografiskt slumpmässiga (256 bitar), bara en hash
  lagras (aldrig själva tokenet), jämförelse i konstant tid,
  engångsbruk, ett token per användare och syfte, och ett tokens slag (verifiering respektive återställning)
  kan inte användas för det andra. Länkarna i mailen öppnar en sida
  med ett formulär; det är formulärets POST som förbrukar tokenet, så att
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
- Skydd mot att någon förhandsregistrerar en annans adress: det finns inget
  lösenord att känna till. Ett förhandsregistrerat konto har bara ett slumpmässigt
  lösenord som kastats, och bara den som kan öppna en länk i brevlådan kan välja
  ett. Länkarna går alltid bara till brevlådans ägare. För en verifierad adress
  gäller "upptaget". Overifierade konton äldre än sju dagar städas bort (se
  uppföljningen nedan). Är mailkvoten
  slut vid en ny registrering ändras ingenting (ingen ny länk, inget mail) men svaret
  är detsamma som för en ny adress.
- Störning via kvoten är accepterad: en anonym part kan förbruka en adress mailkvot
  (3 per timme, gemensam för registrering och "skicka ny länk") och därmed
  tillfälligt hindra att en NY länk skickas, eller ersätta ägarens länk med en ny.
  Ägaren får då alltid den senaste länken i sin inkorg, och alla länkar går
  enbart till brevlådans ägare - ingen kan ta över kontot, bara irritera.
- Mailkvoten per adress förbrukas först när ett mail faktiskt ska skickas,
  så okända adresser aldrig hamnar i begränsarens karta. En anonym part kan
  dock förbruka kvoten för en känd adress och därmed tillfälligt hindra
  mail till den (accepterad störning). Kartan är storleksbegränsad, utgångna
  poster rensas och när den är full nekas nya adresser tyst. Ingen
  IP-baserad begränsning görs (en vidarebefordrad klientadress är
  förfalskningsbar, se [0020](0020-remember-me-hash-based.md)).
- Inlösen av ett token är atomär (den som raderar raden vinner, och lösenordet sätts i
  samma transaktion), utfärdande sker i en transaktion med ett unikt constraint på
  användare och syfte, så två samtidiga anrop kan inte ge två giltiga tokens eller
  dubbelinlösen. En aktivering ändrar aldrig ett redan verifierat kontos lösenord.
- Ett verifierat konto vars adress senare slutar fungera kan inte
  återställas utan manuell hjälp; ingen byte-av-adress-funktion byggs nu.
- Alternativ som valdes bort: att låta länken i mailet förbruka tokenet
  direkt vid GET (enklare, men sårbart för mailskannrar), strikt
  RFC-validering (risk att avvisa korrekta adresser), och att skicka ett
  mail till alla befintliga konton för att bekräfta adressen (onödigt
  friktion för redan etablerade användare).

## Uppföljning (WINE-65): skydd mot massregistrering och städning

Ett efterföljande granskningsvarv gav följande tillägg, utan att ändra något av ovanstående
(avslöjande av verifierad adress vid registrering, svarstidsskillnader och att en anonym part kan
förbruka en adress kvot är fortsatt medvetet accepterat):

- **Globalt mailtak.** Kvoten per adress stoppar inte någon som registrerar många olika adresser.
  Därför finns tak för det totala antalet utgående mail, per timme och per dygn, i TVÅ separata
  budgetar: en gemensam för registrering och "ny länk", och en egen, mindre för "glömt lösenord".
  Uppdelningen gör att ett fullt registreringstak aldrig kan svälta lösenordsåterställning. Restrisk:
  den som tömmer återställningsbudgeten kan blockera återställning för alla en stund, men det kräver
  många begäranden mot VERIFIERADE adresser (okända och overifierade adresser förbrukar inget), och
  varje adress tillåter bara tre per timme. Taken sätts via miljövariabler med
  försiktiga standardvärden och räknas i minnet (nollställs vid omstart, passar enkelinstansdrift).
  Är taket nått skapas inget konto och inget mail skickas, men svaret är detsamma som för en lyckad
  registrering. Timtaket klipps så att det aldrig kan närma sig storleksgränsen på kartan över
  adresser: en full karta skulle annars tyst kunna neka nya adresser. Inget CAPTCHA och ingen
  IP-baserad begränsning.
- **Honeypot.** Registreringsformuläret har ett dolt fält som en människa aldrig fyller i. Är det
  ifyllt skapas inget konto och inget mail skickas, men svaret är detsamma som för en vanlig
  registrering.
- **Kvotplatsen tas atomärt och återbetalas vid fel.** Platsen i kvoten per adress tas innan något
  ändras, så samtidiga anrop aldrig kan släppa igenom fler mail än kvoten. Går själva utskicket
  (som sker asynkront) sedan inte att genomföra återbetalas användarens plats, så ett trasigt
  mailsystem inte gör att användaren förlorar sina försök. Det globala taket återbetalas
  INTE vid ett misslyckat utskick: det räknar försöket, så att en nedlagd SMTP-server inte kan hamras
  obegränsat. Konsekvensen är att en längre SMTP-störning kan tömma budgeten på mail som aldrig gick
  iväg, så att nya registreringar tillfälligt får det neutrala svaret utan mail tills fönstret passerat.
  Platserna lämnas däremot tillbaka (både per adress och globalt) om ingen mailförsändelse alls
  kommer till stånd, t.ex. vid ett databasfel. Mailtrafiken kräver dessutom
  STARTTLS när det är påslaget, så att länkarna aldrig går i klartext om servern inte erbjuder det.
- **Platshållarlösenord utan bcrypt.** Alla overifierade konton delar EN fast, syntaktiskt giltig hash
  med samma kostnadsfaktor som riktiga lösenord. Klartexten var ett slumpmässigt värde på minst 256
  bitar som kasserades, och hashen genererades en gång offline. Säkerheten vilar på att kontot är
  avstängt tills det aktiverats och att klartexten är okänd - hashen är inte hemlig. Den finns för
  tidsjämnhet: registreringen blir billig för servern, medan ett inloggningsförsök mot kontot
  fortfarande kostar en full jämförelse (samma svarstid som ett fel lösenord).
- **Lösenordsbyte är en transaktion med riktade skrivningar.** Förbrukningen av länken, bytet av
  hash och rensningen av övriga länkar sker atomärt: felar något rullas allt tillbaka och länken
  fungerar fortfarande. Aktivering och återställning skriver bara hash (och verifierad-flaggan) och
  sparar aldrig hela användaren, så en samtidig ändring av senaste login eller adminflaggan skrivs
  inte över.
- **Städning vid inloggning.** Samma mönster som för övergivna importmappar
  ([0017](0017-login-triggered-temp-import-cleanup.md)): en lyckad inloggning raderar utgångna länkar
  och overifierade konton äldre än sju dagar (inklusive deras länkar), högst en gång per timme.
  Ett gammalt overifierat konto som fortfarande har en giltig länk (användaren har begärt en ny)
  städas inte bort. Verifierade konton, färska
  overifierade konton och giltiga länkar rörs aldrig. Ett fel i städningen loggas (utan någon
  tokeninformation) och fäller aldrig inloggningen. Ingen schemaläggare införs; en app som ingen
  loggar in i städas alltså inte, vilket är försumbart för dess storlek.
