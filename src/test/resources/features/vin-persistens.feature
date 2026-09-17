# language: sv
Egenskap: Viner överlever en omstart
  Som vinsamlare vill jag att mina viner sparas varaktigt
  så att de inte försvinner om applikationen startas om

  Scenario: Ett vin överlever en omstart av applikationen
    Givet att vinet "Barolo" är sparat i källaren
    När applikationen startas om
    Så ska vinet "Barolo" fortfarande finnas i källaren

  # WINE-50: "Eget betyg" är fri text i databasen (ingen CHECK-constraint
  # längre, till skillnad från "Munskänkarnas betyg") - ett värde som INTE
  # matchar någon av munskänkarnas 29 etiketter måste ändå gå att spara mot
  # en riktig Postgres, inte bara mot den enklare in-minnet-testdubbletten.
  Scenario: Ett fritextvärde för Eget betyg som inte matchar någon av munskänkarnas etiketter sparas ändå
    Givet att vinet "Barolo" med eget betyg "Supergott, dricka nu!" är sparat i källaren
    När applikationen startas om
    Så ska vinet "Barolo" fortfarande ha eget betyg "Supergott, dricka nu!"
