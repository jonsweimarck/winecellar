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

  # "Munskänkarnas betyg" är oförändrat en sluten betygsskala (WINE-50
  # rörde bara "Eget betyg", se scenariot nedan) - sorteringen ska
  # fortfarande följa betygets rangordning, inte etikettens
  # bokstavsordning (annars hade "10 (...)" sorterats FÖRE "9 (...)"
  # eftersom "1" < "9" bokstavsordning, trots att 10 är ett högre betyg).
  Scenario: Sortering på Munskänkarnas betyg använder betygets rangordning, inte bokstavsordning på etiketten
    Givet att källaren innehåller följande viner:
      | namn | munskänkarnas betyg          |
      | Alfa | 9 (9 - 11,5 Medelbra vin)    |
      | Beta | 10 (9 - 11,5 Medelbra vin)   |
    När jag sorterar vinlistan på "Munskänkarnas betyg" i stigande ordning
    Så visas vinerna i ordningen "Alfa, Beta"

  # "Eget betyg" (till skillnad från "Munskänkarnas betyg" ovan) blev fri
  # text i WINE-50 - det finns ingen betygsrangordning kvar att sortera
  # efter. Sorteringen är PROVISORISKT alfabetisk (skiftlägesokänslig,
  # samma som Namn/Producent/Land) - se SortField.OWN_RATING och den
  # öppna arkitekturfrågan i WINE-50-PR:en om det här faktiskt är rätt
  # beteende, eller om fältet i stället borde försöka tolka texten som ett
  # känt betygsnamn, eller tas bort ur sorteringsalternativen helt.
  Scenario: Sortering på Eget betyg (fritext) är alfabetisk, inte betygsrangordning
    Givet att källaren innehåller följande viner:
      | namn | eget betyg                  |
      | Alfa | 9 (9 - 11,5 Medelbra vin)    |
      | Beta | 10 (9 - 11,5 Medelbra vin)   |
    När jag sorterar vinlistan på "Eget betyg" i stigande ordning
    Så visas vinerna i ordningen "Beta, Alfa"
