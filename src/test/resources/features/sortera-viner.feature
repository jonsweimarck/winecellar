# language: sv
Egenskap: Sortera vinlistan
  Som vinsamlare vill jag kunna sortera vinlistan på olika fält och i
  valfri riktning, så att jag snabbt hittar det jag letar efter i en
  stor samling

  Scenario: Sortera på namn, stigande
    Givet att källaren innehåller följande viner:
      | namn     |
      | Chablis  |
      | Albariño |
      | Barolo   |
    När jag sorterar vinlistan på "Namn" i stigande ordning
    Så visas vinerna i ordningen "Albariño, Barolo, Chablis"

  Scenario: Sortera på namn, fallande
    Givet att källaren innehåller följande viner:
      | namn     |
      | Chablis  |
      | Albariño |
      | Barolo   |
    När jag sorterar vinlistan på "Namn" i fallande ordning
    Så visas vinerna i ordningen "Chablis, Barolo, Albariño"

  Scenario: Sortera på årgång
    Givet att källaren innehåller följande viner:
      | namn     | årgång |
      | Barolo   | 2018   |
      | Chablis  | 2020   |
      | Albariño | 2022   |
    När jag sorterar vinlistan på "Årgång" i fallande ordning
    Så visas vinerna i ordningen "Albariño, Chablis, Barolo"

  Scenario: Viner utan värde för det sorterade fältet hamnar sist, oavsett riktning
    Givet att källaren innehåller följande viner:
      | namn     | eget betyg                      |
      | Barolo   | 16 (15 - 17,5 Högklassigt vin)   |
      | Albariño |                                  |
      | Chablis  | 19 (18 - 20 Exceptionellt vin)   |
    När jag sorterar vinlistan på "Eget betyg" i fallande ordning
    Så visas vinerna i ordningen "Chablis, Barolo, Albariño"

  Scenario: Sortering på betyg använder betygets rangordning, inte bokstavsordning på etiketten
    Givet att källaren innehåller följande viner:
      | namn | eget betyg                  |
      | Alfa | 9 (9 - 11,5 Medelbra vin)    |
      | Beta | 10 (9 - 11,5 Medelbra vin)   |
    När jag sorterar vinlistan på "Eget betyg" i stigande ordning
    Så visas vinerna i ordningen "Alfa, Beta"
