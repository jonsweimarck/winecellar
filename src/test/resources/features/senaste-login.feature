# language: sv
Egenskap: Senaste inloggning
  Som admin vill jag se när varje konto skapades och när det senast loggade in
  så att jag kan förvalta kontona i appen

  # Körs mot en riktig Postgres (Testcontainers) och den riktiga
  # säkerhetskedjan (POST /login) - inte InMemory-dubbletter. Remember-me-
  # återinloggning testas i AdminControllerTest (kräver en konfigurerad nyckel).

  Scenario: Ett nytt konto har senaste login satt från början
    Givet att ett konto med användarnamnet "nyanvandare" redan finns
    Så ska senaste login för "nyanvandare" vara samma tidpunkt som när kontot skapades

  Scenario: En lyckad inloggning uppdaterar senaste login
    Givet att ett konto med användarnamnet "alice" redan finns
    Och att senaste login för "alice" är satt till 2020-01-01 10:00 UTC
    När "alice" loggar in med rätt lösenord
    Så ska senaste login för "alice" vara senare än 2020-01-01 10:00 UTC

  Scenario: En misslyckad inloggning ändrar inte senaste login
    Givet att ett konto med användarnamnet "alice" redan finns
    Och att senaste login för "alice" är satt till 2020-01-01 10:00 UTC
    När "alice" försöker logga in med fel lösenord
    Så ska senaste login för "alice" fortfarande vara 2020-01-01 10:00 UTC

  Scenario: Senaste login är obligatoriskt i databasen
    Givet att ett konto med användarnamnet "alice" redan finns
    Så ska databasen vägra att tömma senaste login för "alice"

  Scenario: Att göra någon till admin bevarar senaste login
    Givet att "alice" är admin
    Och att "bob" är en vanlig användare
    Och att senaste login för "bob" är satt till 2020-01-01 10:00 UTC
    När "alice" gör "bob" till admin
    Så ska senaste login för "bob" fortfarande vara 2020-01-01 10:00 UTC
