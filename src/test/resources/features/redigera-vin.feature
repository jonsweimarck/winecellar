# language: sv
Egenskap: Redigera ett vin
  Som vinsamlare vill jag kunna ändra uppgifter om ett vin
  så att antal flaskor och andra fält stämmer över tid

  Scenario: Ändra antal flaskor efter att en flaska druckits
    Givet att vinet "Barolo" finns med 3 flaskor
    När jag ändrar antalet flaskor för "Barolo" till 2
    Så ska vinet "Barolo" visas med 2 flaskor

  Scenario: Öka antalet flaskor med mer än en, t.ex. vid en dubblettträff i importen
    Givet att vinet "Barolo" finns med 3 flaskor
    När jag ökar antalet flaskor för "Barolo" med 2
    Så ska vinet "Barolo" visas med 5 flaskor
