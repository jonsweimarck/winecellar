# language: sv
Egenskap: Viner överlever en omstart
  Som vinsamlare vill jag att mina viner sparas varaktigt
  så att de inte försvinner om applikationen startas om

  Scenario: Ett vin överlever en omstart av applikationen
    Givet att vinet "Barolo" är sparat i källaren
    När applikationen startas om
    Så ska vinet "Barolo" fortfarande finnas i källaren
