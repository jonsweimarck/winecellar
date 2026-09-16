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

  # WINE-50 (se ADR 0022): "Eget betyg" (fri text) sorteras med en ren,
  # skiftlägesokänslig strängjämförelse - exakt samma mönster och
  # riktningssemantik som NAME/PRODUCER/COUNTRY, ingen specialbehandling.
  # Det null-hanteringen (Albariño sist) som scenariot egentligen
  # verifierar är oförändrad, oavsett riktning.
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

  # WINE-50 (se ADR 0022): "Eget betyg" (till skillnad från "Munskänkarnas
  # betyg" ovan) blev fri text - ingen Rating.fromLabel-igenkänning byggs
  # (för komplext för ett fält som är fri text per design), och fältet
  # togs INTE bort ur sorteringsalternativen. Sorteringen är i stället en
  # ren, skiftlägesokänslig strängjämförelse - EXAKT samma riktnings-
  # semantik som NAME/PRODUCER/COUNTRY, ingen specialbehandling. Ger
  # alltså INTE en betygsrangordning (till skillnad från Munskänkarnas
  # betyg ovan) - "10 ..." hamnar här FÖRE "9 ..." i stigande ordning,
  # eftersom "1" < "9" bokstavsordning. Ett medvetet accepterat beteende
  # för ett fält som är fri text per design, inte en bugg att fixa genom
  # att börja tolka texten.
  Scenario: Sortering på Eget betyg (fritext) använder ren alfabetisk ordning, till skillnad från Munskänkarnas betyg
    Givet att källaren innehåller följande viner:
      | namn | eget betyg                  |
      | Alfa | 9 (9 - 11,5 Medelbra vin)    |
      | Beta | 10 (9 - 11,5 Medelbra vin)   |
    När jag sorterar vinlistan på "Eget betyg" i stigande ordning
    Så visas vinerna i ordningen "Beta, Alfa"
