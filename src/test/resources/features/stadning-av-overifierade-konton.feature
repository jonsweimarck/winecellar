# language: sv
Egenskap: Städning av gamla verifieringsrester vid inloggning
  Som driftansvarig vill jag att utgångna länkar och övergivna overifierade konton städas bort
  så att databasen inte växer av förhandsregistreringar som aldrig bekräftats

  # Körs mot riktig Postgres och den riktiga inloggningskedjan (WINE-65, ADR 0026/0017): städningen
  # triggas av en lyckad inloggning. Verifierade konton, färska overifierade konton och giltiga
  # länkar rörs aldrig. Ett fel i städningen får aldrig fälla inloggningen.

  Bakgrund:
    Givet att det finns ett verifierat konto "anna@example.com" med lösenordet "hemligt123"

  Scenario: Utgångna länkar städas bort men giltiga ligger kvar
    Givet ett overifierat konto "utgangen@example.com" skapat för 1 dagar sedan
    Och kontot "utgangen@example.com" har ett utgånget verifieringstoken
    Och ett overifierat konto "giltig@example.com" skapat för 1 dagar sedan
    Och kontot "giltig@example.com" har ett giltigt verifieringstoken
    När jag loggar in som "anna@example.com" med lösenordet "hemligt123"
    Så lyckas inloggningen
    Och har kontot "utgangen@example.com" inga verifieringstokens
    Och har kontot "giltig@example.com" ett verifieringstoken

  Scenario: Overifierade konton äldre än sju dagar raderas tillsammans med sina länkar
    Givet ett overifierat konto "gammal@example.com" skapat för 8 dagar sedan
    Och kontot "gammal@example.com" har ett utgånget verifieringstoken
    Och ett overifierat konto "farsk@example.com" skapat för 6 dagar sedan
    Och kontot "farsk@example.com" har ett giltigt verifieringstoken
    Och ett verifierat konto "veteran@example.com" skapat för 400 dagar sedan
    När jag loggar in som "anna@example.com" med lösenordet "hemligt123"
    Så lyckas inloggningen
    Och finns kontot "gammal@example.com" inte längre
    Och har kontot "gammal@example.com" inga verifieringstokens
    Och finns kontot "farsk@example.com" kvar
    Och har kontot "farsk@example.com" ett verifieringstoken
    Och finns kontot "veteran@example.com" kvar
    Och finns kontot "anna@example.com" kvar

  Scenario: Ett gammalt overifierat konto med ett fortfarande giltigt token lever kvar
    Givet ett overifierat konto "nylink@example.com" skapat för 8 dagar sedan
    Och kontot "nylink@example.com" har ett giltigt verifieringstoken
    När jag loggar in som "anna@example.com" med lösenordet "hemligt123"
    Så lyckas inloggningen
    Och finns kontot "nylink@example.com" kvar
    Och har kontot "nylink@example.com" ett verifieringstoken

  Scenario: Ett fel i städningen påverkar inte inloggningen
    Givet ett overifierat konto "utgangen@example.com" skapat för 1 dagar sedan
    Och kontot "utgangen@example.com" har ett utgånget verifieringstoken
    Och att städningen kommer att misslyckas
    När jag loggar in som "anna@example.com" med lösenordet "hemligt123"
    Så lyckas inloggningen
    Och finns kontot "utgangen@example.com" kvar
