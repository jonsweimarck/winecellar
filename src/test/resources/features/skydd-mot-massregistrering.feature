# language: sv
Egenskap: Skydd mot massregistrering och mailmissbruk
  Som driftansvarig vill jag att appen inte kan användas för att skicka tusentals mail
  så att SMTP-kvoten, avsändarens rykte och kostnaden inte hamnar i händerna på en anonym part

  # Körs mot riktig Postgres och den riktiga säkerhetskedjan, med en flyttbar testklocka och en
  # mail-fake (WINE-65, ADR 0026). Taken är i minnet och gäller ALLA mailtyper gemensamt. Ett slut
  # på taket ändrar ingenting men ger exakt samma svar som en lyckad registrering - ingen CAPTCHA
  # och ingen IP-baserad begränsning. Taken testas med egna, små tak (annars är de höga).

  Scenario: Globalt timtak: ingen ny registrering när taket är nått
    Givet att det globala mailtaket är 3 mail per timme och 100 per dygn
    Och att 3 mail redan har skickats
    När jag registrerar mig med e-postadressen "ny@example.com"
    Så skickas inget mail till "ny@example.com"
    Och inget konto med användarnamnet "ny@example.com" har skapats
    Och svaret till användaren är det neutrala "Kolla din e-post"

  Scenario: Globalt dygnstak: ingen ny registrering när dygnstaket är nått
    Givet att det globala mailtaket är 100 mail per timme och 3 per dygn
    Och att 3 mail redan har skickats
    Och att det har gått 2 timmar
    När jag registrerar mig med e-postadressen "ny@example.com"
    Så skickas inget mail till "ny@example.com"
    Och inget konto med användarnamnet "ny@example.com" har skapats
    Och svaret till användaren är det neutrala "Kolla din e-post"

  Scenario: Timtaket återställs när klockan flyttats fram förbi fönstret
    Givet att det globala mailtaket är 3 mail per timme och 100 per dygn
    Och att 3 mail redan har skickats
    Och att det har gått 61 minuter
    När jag registrerar mig med e-postadressen "ny@example.com"
    Så finns ett overifierat konto "ny@example.com"
    Och ett mail med en verifieringslänk har skickats till "ny@example.com"

  Scenario: Dygnstaket återställs när ett dygn gått
    Givet att det globala mailtaket är 100 mail per timme och 3 per dygn
    Och att 3 mail redan har skickats
    Och att det har gått 25 timmar
    När jag registrerar mig med e-postadressen "ny@example.com"
    Så finns ett overifierat konto "ny@example.com"

  Scenario: Registrering och ny länk delar tak
    Givet att det globala mailtaket är 3 mail per timme och 100 per dygn
    Och att jag har registrerat mig med e-postadressen "forst@example.com"
    Och att jag har begärt en ny verifieringslänk för "forst@example.com" 2 gånger
    När jag registrerar mig med e-postadressen "ny@example.com"
    Så skickas inget mail till "ny@example.com"
    Och inget konto med användarnamnet "ny@example.com" har skapats
    När jag begär en ny verifieringslänk för "forst@example.com"
    Så har exakt 3 verifieringsmail skickats till "forst@example.com"

  Scenario: Ett fullt registreringstak svälter inte lösenordsåterställning
    Givet att det globala mailtaket är 3 mail per timme och 100 per dygn
    Och att det finns ett verifierat konto "verifierad@example.com" med lösenordet "hemligt123"
    Och att 3 mail redan har skickats
    När jag registrerar mig med e-postadressen "ny@example.com"
    Så skickas inget mail till "ny@example.com"
    När ett återställningsmail begärs för "verifierad@example.com"
    Så ett mail med en återställningslänk har skickats till "verifierad@example.com"

  Scenario: Honeypot: ett ifyllt dolt fält ger inget konto och inget mail men samma svar
    När en bot registrerar sig med e-postadressen "bot@example.com" och fyller i det dolda fältet
    Så skickas inget mail till "bot@example.com"
    Och inget konto med användarnamnet "bot@example.com" har skapats
    Och svaret är detsamma som för en vanlig registrering

  Scenario: Ett misslyckat mailutskick kostar ingen kvotplats
    Givet att mailutskicken misslyckas
    Och att jag har registrerat mig med e-postadressen "anna@example.com"
    Och att jag har begärt en ny verifieringslänk för "anna@example.com" 4 gånger
    Och att mailutskicken fungerar igen
    När jag begär en ny verifieringslänk för "anna@example.com"
    Så har exakt 1 verifieringsmail skickats till "anna@example.com"
    Och den nya verifieringslänken låter mig välja lösenordet "hemligt123" för "anna@example.com"
