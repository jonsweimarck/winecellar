# language: sv
Egenskap: Registrering med e-postverifiering
  Som ny besökare vill jag registrera mig med bara min e-postadress, bekräfta den via ett mail
  och där välja mitt lösenord
  så att bara den som äger adressen kan skapa och använda kontot

  # Körs mot riktig Postgres och den riktiga säkerhetskedjan (POST /login och POST /verifiera
  # via MockMvc). Mail fångas av en fake, och tiden flyttas med en testklocka. Registreringen
  # frågar inte efter något lösenord (WINE-59, ADR 0026): ett overifierat konto har ett
  # slumpmässigt, oanvändbart lösenord, och lösenordet väljs först på verifieringslänken.

  Scenario: Registrering kräver en giltig e-postadress som användarnamn
    När jag försöker registrera mig med e-postadressen "inte-en-epost"
    Så avvisas registreringen eftersom användarnamnet måste vara en e-postadress
    Och inget konto med användarnamnet "inte-en-epost" har skapats

  Scenario: Plustecken i e-postadressen accepteras
    När jag registrerar mig med e-postadressen "anna+vin@example.com"
    Så finns ett overifierat konto "anna+vin@example.com"

  Scenario: Giltig registrering med bara e-post skapar ett overifierat konto och skickar ett verifieringsmail
    När jag registrerar mig med e-postadressen "anna@example.com"
    Så finns ett overifierat konto "anna@example.com"
    Och kontot "anna@example.com" har ett oanvändbart slumpmässigt lösenord som ingen känner till
    Och ett mail med en verifieringslänk har skickats till "anna@example.com"
    Och verifieringstokenet för "anna@example.com" är bara lagrat som en hash

  Scenario: Ett overifierat konto kan inte logga in
    Givet att jag har registrerat mig med e-postadressen "anna@example.com"
    När jag försöker logga in som "anna@example.com" med lösenordet "hemligt123"
    Så nekas inloggningen utan att avslöja att kontot finns, och sidan erbjuder en ny verifieringslänk

  Scenario: Verifieringslänken leder till lösenordsval och aktiverar kontot
    Givet att jag har registrerat mig med e-postadressen "anna@example.com"
    När jag väljer lösenordet "hemligt123" via verifieringslänken i mailet till "anna@example.com"
    Så är kontot "anna@example.com" verifierat
    Och jag kan logga in som "anna@example.com" med lösenordet "hemligt123"

  Scenario: Verifieringslänken kan inte användas två gånger
    Givet att jag har registrerat mig med e-postadressen "anna@example.com"
    Och att jag har valt lösenordet "hemligt123" via verifieringslänken i mailet till "anna@example.com"
    När jag väljer lösenordet "annat12345" via verifieringslänken i mailet till "anna@example.com"
    Så avvisas länken som ogiltig
    Och jag kan inte logga in som "anna@example.com" med lösenordet "annat12345"
    Och jag kan logga in som "anna@example.com" med lösenordet "hemligt123"

  Scenario: Verifieringslänken slutar gälla efter 24 timmar
    Givet att jag har registrerat mig med e-postadressen "anna@example.com"
    Och att det har gått 25 timmar
    När jag väljer lösenordet "hemligt123" via verifieringslänken i mailet till "anna@example.com"
    Så avvisas länken som utgången
    Och kontot "anna@example.com" är fortfarande overifierat

  Scenario: Ett tomt eller felaktigt bekräftat lösenord avvisas utan att länken förbrukas
    Givet att jag har registrerat mig med e-postadressen "anna@example.com"
    När jag försöker välja lösenordet "hemligt123" med bekräftelsen "annat" via verifieringslänken i mailet till "anna@example.com"
    Så avvisas lösenordet utan att länken förbrukas
    Och den senaste verifieringslänken till "anna@example.com" fungerar fortfarande
    När jag försöker välja lösenordet "" med bekräftelsen "" via verifieringslänken i mailet till "anna@example.com"
    Så avvisas lösenordet utan att länken förbrukas
    Och kontot "anna@example.com" är fortfarande overifierat
    När jag väljer lösenordet "hemligt123" via verifieringslänken i mailet till "anna@example.com"
    Så är kontot "anna@example.com" verifierat

  Scenario: En ny verifieringslänk kan begäras och gör de gamla ogiltiga
    Givet att jag har registrerat mig med e-postadressen "anna@example.com"
    När jag begär en ny verifieringslänk för "anna@example.com"
    Så avvisas den första verifieringslänken som ogiltig
    Och den nya verifieringslänken låter mig välja lösenordet "hemligt123" för "anna@example.com"

  Scenario: E-postadresser är unika oavsett versaler
    Givet att ett konto med användarnamnet "Anna@Example.com" redan finns
    När jag försöker registrera mig med e-postadressen "anna@example.com"
    Så avvisas registreringen eftersom användarnamnet är upptaget

  Scenario: Någon som förhandsregistrerar en annans adress kan inte ta över kontot
    Givet att jag har registrerat mig med e-postadressen "offer@example.com"
    Så jag kan inte logga in som "offer@example.com" med lösenordet "angripare123"
    Och kontot "offer@example.com" har ett oanvändbart slumpmässigt lösenord som ingen känner till
    När någon försöker välja lösenordet "angripare123" via en påhittad verifieringslänk
    Så avvisas länken som ogiltig
    Och kontot "offer@example.com" är fortfarande overifierat
    När jag registrerar mig med e-postadressen "offer@example.com"
    Och jag väljer lösenordet "offerLösen456" via verifieringslänken i mailet till "offer@example.com"
    Så är kontot "offer@example.com" verifierat
    Och jag kan logga in som "offer@example.com" med lösenordet "offerLösen456"
    Och angriparens lösenord "angripare123" fungerar inte för "offer@example.com"

  Scenario: Den äldre länken blir ogiltig när en ny registrering skickar en ny
    Givet att jag har registrerat mig med e-postadressen "offer@example.com"
    När jag registrerar mig med e-postadressen "offer@example.com"
    Så avvisas den första verifieringslänken som ogiltig
    Och har exakt 2 verifieringsmail skickats till "offer@example.com"

  Scenario: Tömd mailkvot ändrar ingenting och ger ett neutralt svar
    Givet att jag har registrerat mig med e-postadressen "offer@example.com"
    Och att jag har registrerat mig igen med e-postadressen "offer@example.com" 2 gånger
    När jag registrerar mig med e-postadressen "offer@example.com"
    Så blir registreringen godkänd precis som för en ny adress
    Och har exakt 3 verifieringsmail skickats till "offer@example.com"
    Och den senaste verifieringslänken till "offer@example.com" fungerar fortfarande
    När jag väljer lösenordet "offerLösen456" via verifieringslänken i mailet till "offer@example.com"
    Så är kontot "offer@example.com" verifierat

  Scenario: Nytt verifieringsmail kan begäras när kvotfönstret gått ut
    Givet att jag har registrerat mig med e-postadressen "offer@example.com"
    Och att jag har begärt en ny verifieringslänk för "offer@example.com" 2 gånger
    Och att det har gått 61 minuter
    När jag begär en ny verifieringslänk för "offer@example.com"
    Så har exakt 4 verifieringsmail skickats till "offer@example.com"
    Och den nya verifieringslänken låter mig välja lösenordet "offerLösen456" för "offer@example.com"
    Och jag kan logga in som "offer@example.com" med lösenordet "offerLösen456"
