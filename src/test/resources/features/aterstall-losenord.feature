# language: sv
Egenskap: Återställning av glömt lösenord
  Som användare som glömt mitt lösenord vill jag kunna välja ett nytt via ett mail
  så att jag kommer in i mitt konto igen

  # Körs mot riktig Postgres och den riktiga säkerhetskedjan via MockMvc.
  # Mail fångas av en fake, och tiden flyttas med en testklocka.

  Scenario: Inloggningssidan länkar till återställningsformuläret
    När jag öppnar inloggningssidan
    Så finns länken "Glömt ditt lösenord?" till återställningsformuläret

  Scenario: Begäran för en känd adress mailar en återställningslänk
    Givet att det finns ett verifierat konto "anna@example.com" med lösenordet "hemligt123"
    När jag begär lösenordsåterställning för "anna@example.com"
    Så ett mail med en återställningslänk har skickats till "anna@example.com"
    Och sidan visar det neutrala svaret "Om adressen finns har ett mail skickats"

  Scenario: Begäran för en okänd adress ger exakt samma svar men inget mail
    Givet att det finns ett verifierat konto "anna@example.com" med lösenordet "hemligt123"
    När jag begär lösenordsåterställning för "anna@example.com"
    Och jag begär lösenordsåterställning för "okand@example.com"
    Så ger de två begäranden exakt samma svar
    Och inget mail har skickats till "okand@example.com"

  Scenario: Nytt lösenord via giltig länk
    Givet att det finns ett verifierat konto "anna@example.com" med lösenordet "hemligt123"
    Och att jag har begärt lösenordsåterställning för "anna@example.com"
    När jag väljer lösenordet "nyttLösen456" via länken i mailet till "anna@example.com"
    Så jag kan logga in som "anna@example.com" med lösenordet "nyttLösen456"
    Och jag kan inte logga in som "anna@example.com" med lösenordet "hemligt123"

  Scenario: Återställningslänken kan inte återanvändas
    Givet att det finns ett verifierat konto "anna@example.com" med lösenordet "hemligt123"
    Och att jag har begärt lösenordsåterställning för "anna@example.com"
    Och att jag har valt lösenordet "nyttLösen456" via länken i mailet till "anna@example.com"
    När jag väljer lösenordet "annatLösen789" via länken i mailet till "anna@example.com"
    Så avvisas länken som ogiltig
    Och jag kan inte logga in som "anna@example.com" med lösenordet "annatLösen789"

  Scenario: Återställningslänken gäller i 1 timme
    Givet att det finns ett verifierat konto "anna@example.com" med lösenordet "hemligt123"
    Och att jag har begärt lösenordsåterställning för "anna@example.com"
    Och att det har gått 61 minuter
    När jag väljer lösenordet "nyttLösen456" via länken i mailet till "anna@example.com"
    Så avvisas länken som utgången
    Och jag kan logga in som "anna@example.com" med lösenordet "hemligt123"

  Scenario: En ny begäran gör tidigare länkar ogiltiga
    Givet att det finns ett verifierat konto "anna@example.com" med lösenordet "hemligt123"
    Och att jag har begärt lösenordsåterställning för "anna@example.com"
    Och att jag har begärt lösenordsåterställning för "anna@example.com"
    Så fungerar bara den senaste återställningslänken för "anna@example.com"

  Scenario: Ett lösenordsbyte avslutar övriga inloggade sessioner
    Givet att det finns ett verifierat konto "anna@example.com" med lösenordet "hemligt123"
    Och att "anna@example.com" är inloggad i en session
    Och att jag har begärt lösenordsåterställning för "anna@example.com"
    När jag väljer lösenordet "nyttLösen456" via länken i mailet till "anna@example.com"
    Så är den tidigare sessionen för "anna@example.com" avslutad

  Scenario: Många återställningsbegäranden i rad ger bara ett begränsat antal mail
    Givet att det finns ett verifierat konto "anna@example.com" med lösenordet "hemligt123"
    När jag begär lösenordsåterställning för "anna@example.com" 10 gånger i rad
    Så har exakt 3 återställningsmail skickats till "anna@example.com"
    Och sidan visar fortfarande det neutrala svaret "Om adressen finns har ett mail skickats"
