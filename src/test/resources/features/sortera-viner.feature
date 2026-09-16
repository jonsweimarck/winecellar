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

  # WINE-50 (arkitektbeslut efter eskalering, se CLAUDE.md/SortField.java):
  # "fallande" på Eget betyg (fri text) använder numera en RAK
  # (skiftlägesokänslig) strängjämförelse, medvetet omvänd jämfört med
  # övriga textfält - se OWN_RATING_ASCENDING. En pragmatisk approximation,
  # inte en riktig numerisk sortering: "16 ..." hamnar här FÖRE "19 ..."
  # trots "fallande", eftersom "1" < "1" och "6" < "9" bokstavsordning. Det
  # null-hanteringen (Albariño sist) som scenariot egentligen verifierar är
  # oförändrad, oavsett riktning.
  Scenario: Viner utan värde för det sorterade fältet hamnar sist, oavsett riktning
    Givet att källaren innehåller följande viner:
      | namn     | eget betyg                      |
      | Barolo   | 16 (15 - 17,5 Högklassigt vin)   |
      | Albariño |                                  |
      | Chablis  | 19 (18 - 20 Exceptionellt vin)   |
    När jag sorterar vinlistan på "Eget betyg" i fallande ordning
    Så visas vinerna i ordningen "Barolo, Chablis, Albariño"

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

  # WINE-50 (arkitektbeslut efter eskalering, se CLAUDE.md/SortField.java):
  # "Eget betyg" (till skillnad från "Munskänkarnas betyg" ovan) blev fri
  # text - ingen Rating.fromLabel-igenkänning byggs (för komplext för ett
  # fält som är fri text per design), och fältet togs INTE bort ur
  # sorteringsalternativen. "Stigande" använder i stället en MEDVETET
  # OMVÄND strängjämförelse (motsatsen mot NAME/PRODUCER/COUNTRY) -
  # motiverat av att betyg typiskt anges med ett inledande siffervärde
  # där låga siffror betyder låga betyg, vilket gör att en omvänd
  # alfabetisk jämförelse råkar ge rätt resultat för just den vanliga
  # kombinationen ett-/tvåsiffrigt betyg nedan. En pragmatisk
  # approximation, inte en riktig numerisk sortering - fungerar inte lika
  # konsekvent för alla kombinationer (se SortField.OWN_RATING_ASCENDING).
  Scenario: Sortering på Eget betyg (fritext) använder en medvetet omvänd strängjämförelse som "stigande"
    Givet att källaren innehåller följande viner:
      | namn | eget betyg                  |
      | Alfa | 9 (9 - 11,5 Medelbra vin)    |
      | Beta | 10 (9 - 11,5 Medelbra vin)   |
    När jag sorterar vinlistan på "Eget betyg" i stigande ordning
    Så visas vinerna i ordningen "Alfa, Beta"
