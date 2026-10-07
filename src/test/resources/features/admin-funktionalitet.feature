# language: sv
Egenskap: Adminfunktionalitet
  Som admin vill jag kunna göra andra användare till admin och radera användare
  så att jag kan förvalta kontona i appen

  # Körs mot en riktig Postgres (Testcontainers) - inget ON DELETE CASCADE finns
  # i schemat, så raderingens FK-ordning måste bevisas mot den riktiga databasen.

  Scenario: En ny användare är aldrig admin
    Givet att ett konto med användarnamnet "nyanvandare" redan finns
    Så ska "nyanvandare" inte vara admin

  Scenario: En admin kan göra en annan användare till admin
    Givet att "alice" är admin
    Och att "bob" är en vanlig användare
    När "alice" gör "bob" till admin
    Så ska "bob" vara admin

  Scenario: En vanlig användare kan inte göra någon till admin
    Givet att "alice" är en vanlig användare
    Och att "bob" är en vanlig användare
    När "alice" försöker göra "bob" till admin
    Så ska "bob" inte vara admin
    Och ska "alice" inte vara admin

  Scenario: Radering av en användare raderar också hens viner, taggar, konversationer och meddelanden
    Givet att "alice" är admin
    Och att "bob" är en vanlig användare
    Och att "bob" äger vinet "Barolo" med taggen "Favorit" och en konversation med frågan "Vad passar till Barolo?"
    När "alice" raderar användaren "bob"
    Så ska användaren "bob" inte finnas
    Och ska inga viner, taggar, konversationer eller meddelanden som tillhörde "bob" finnas kvar

  Scenario: Radering av en användare påverkar inte andra användares data
    Givet att "alice" är admin
    Och att "bob" är en vanlig användare
    Och att "carol" är en vanlig användare
    Och att "bob" äger vinet "Barolo" med taggen "Favorit" och en konversation med frågan "Vad passar till Barolo?"
    Och att "carol" äger vinet "Chianti" med taggen "Vardag" och en konversation med frågan "Vad passar till Chianti?"
    När "alice" raderar användaren "bob"
    Så ska användaren "carol" fortfarande finnas
    Och ska "carol" fortfarande äga vinet "Chianti" med taggen "Vardag" och en konversation med frågan "Vad passar till Chianti?"

  Scenario: En vanlig användare kan inte radera någon
    Givet att "alice" är en vanlig användare
    Och att "bob" är en vanlig användare
    Och att "bob" äger vinet "Barolo" med taggen "Favorit" och en konversation med frågan "Vad passar till Barolo?"
    När "alice" försöker radera användaren "bob"
    Så ska användaren "bob" fortfarande finnas
    Och ska "bob" fortfarande äga vinet "Barolo" med taggen "Favorit" och en konversation med frågan "Vad passar till Barolo?"

  Scenario: En admin kan inte radera sig själv
    Givet att "alice" är admin
    När "alice" försöker radera användaren "alice"
    Så ska användaren "alice" fortfarande finnas

  Scenario: Bara en admin ser listan över användare
    Givet att "alice" är admin
    Och att "bob" är en vanlig användare
    Så ska "alice" se användarna "alice" och "bob" i adminlistan
    Och ska "bob" inte se några användare i adminlistan
