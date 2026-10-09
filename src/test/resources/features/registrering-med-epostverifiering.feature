# language: sv
Egenskap: Registrering med e-postverifiering
  Som ny besökare vill jag registrera mig med min e-postadress och bekräfta den via ett mail
  så att bara den som äger adressen kan skapa och använda kontot

  # Körs mot riktig Postgres och den riktiga säkerhetskedjan (POST /login via
  # MockMvc). Mail fångas av en fake, och tiden flyttas med en testklocka.
  # "Jag är inloggad direkt efter registrering" finns inte längre (WINE-59).

  Scenario: Registrering kräver en giltig e-postadress som användarnamn
    När jag försöker registrera mig med användarnamnet "inte-en-epost" och lösenordet "hemligt123"
    Så avvisas registreringen eftersom användarnamnet måste vara en e-postadress
    Och inget konto med användarnamnet "inte-en-epost" har skapats

  Scenario: Giltig registrering skapar ett overifierat konto och skickar ett verifieringsmail
    När jag registrerar mig med användarnamnet "anna@example.com" och lösenordet "hemligt123"
    Så finns ett overifierat konto "anna@example.com"
    Och ett mail med en verifieringslänk har skickats till "anna@example.com"
    Och verifieringstokenet för "anna@example.com" är bara lagrat som en hash

  Scenario: Ett overifierat konto kan inte logga in
    Givet att jag har registrerat mig med användarnamnet "anna@example.com" och lösenordet "hemligt123"
    När jag försöker logga in som "anna@example.com" med lösenordet "hemligt123"
    Så nekas inloggningen med ett meddelande om att adressen måste verifieras

  Scenario: Verifieringslänken aktiverar kontot
    Givet att jag har registrerat mig med användarnamnet "anna@example.com" och lösenordet "hemligt123"
    När jag öppnar verifieringslänken i mailet till "anna@example.com"
    Så är kontot "anna@example.com" verifierat
    Och jag kan logga in som "anna@example.com" med lösenordet "hemligt123"

  Scenario: Verifieringslänken kan inte användas två gånger
    Givet att jag har registrerat mig med användarnamnet "anna@example.com" och lösenordet "hemligt123"
    Och att jag har öppnat verifieringslänken i mailet till "anna@example.com"
    När jag öppnar verifieringslänken i mailet till "anna@example.com" igen
    Så avvisas länken som ogiltig

  Scenario: Verifieringslänken slutar gälla efter 24 timmar
    Givet att jag har registrerat mig med användarnamnet "anna@example.com" och lösenordet "hemligt123"
    Och att det har gått 25 timmar
    När jag öppnar verifieringslänken i mailet till "anna@example.com"
    Så avvisas länken som utgången
    Och kontot "anna@example.com" är fortfarande overifierat

  Scenario: En ny verifieringslänk kan begäras och gör de gamla ogiltiga
    Givet att jag har registrerat mig med användarnamnet "anna@example.com" och lösenordet "hemligt123"
    När jag begär en ny verifieringslänk för "anna@example.com"
    Så avvisas den första verifieringslänken som ogiltig
    Och den nya verifieringslänken aktiverar kontot "anna@example.com"

  Scenario: E-postadresser är unika oavsett versaler
    Givet att ett konto med användarnamnet "Anna@Example.com" redan finns
    När jag försöker registrera mig med användarnamnet "anna@example.com" och lösenordet "hemligt123"
    Så avvisas registreringen eftersom användarnamnet är upptaget

  Scenario: Offrets registrering skriver över en förhandsregistrering med angriparens lösenord
    Givet att jag har registrerat mig med användarnamnet "offer@example.com" och lösenordet "angripare123"
    När jag registrerar mig med användarnamnet "offer@example.com" och lösenordet "offerLösen456"
    Så blir registreringen godkänd precis som för en ny adress
    Och angriparens verifieringslänk till "offer@example.com" är ogiltig
    Och den senaste verifieringslänken till "offer@example.com" aktiverar kontot
    Och angriparens lösenord "angripare123" fungerar inte för "offer@example.com"
    Och jag kan logga in som "offer@example.com" med lösenordet "offerLösen456"

  Scenario: Ett verifierat konto kan inte skrivas över genom ny registrering
    Givet att ett konto med användarnamnet "Anna@Example.com" redan finns
    När jag försöker registrera mig med användarnamnet "anna@example.com" och lösenordet "angripare123"
    Så avvisas registreringen eftersom användarnamnet är upptaget
    Och jag kan inte logga in som "anna@example.com" med lösenordet "angripare123"

  Scenario: Offrets omregistrering skriver över lösenordet även när mailkvoten är slut
    Givet att jag har registrerat mig med användarnamnet "offer@example.com" och lösenordet "angripare123"
    Och att jag har begärt en ny verifieringslänk för "offer@example.com" 3 gånger
    När jag registrerar mig med användarnamnet "offer@example.com" och lösenordet "offerLösen456"
    Så blir registreringen godkänd precis som för en ny adress
    Och alla verifieringslänkar utom den senaste till "offer@example.com" är ogiltiga
    Och angriparens lösenord "angripare123" fungerar inte för "offer@example.com"

  Scenario: Tömd kvot för "ny länk" hindrar inte omregistrering från att skicka ett ersättningsmail
    Givet att jag har registrerat mig med användarnamnet "offer@example.com" och lösenordet "angripare123"
    Och att jag har begärt en ny verifieringslänk för "offer@example.com" 3 gånger
    När jag registrerar mig med användarnamnet "offer@example.com" och lösenordet "offerLösen456"
    Så har ett ersättningsmail med enda giltiga verifieringslänken skickats till "offer@example.com"
    Och angriparens lösenord "angripare123" fungerar inte för "offer@example.com"
