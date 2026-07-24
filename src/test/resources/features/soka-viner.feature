# language: sv
Egenskap: Fritextsök i vinlistan
  Som vinsamlare vill jag kunna fritextsöka över namn, producent, druvor,
  tasting notes, Systembolagets beskrivning och Munskänkarnas bedömning,
  så att jag snabbt hittar ett visst vin i en stor samling - t.ex. alla
  viner gjorda på en viss druva

  Scenario: Sökning matchar på namn
    Givet att källaren innehåller följande viner:
      | namn    |
      | Barolo  |
      | Chablis |
    När jag söker efter "barolo"
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Chablis"

  Scenario: Sökning matchar på producent
    Givet att källaren innehåller följande viner:
      | namn    | producent  |
      | Barolo  | Pio Cesare |
      | Chablis | Domaine X  |
    När jag söker efter "cesare"
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Chablis"

  Scenario: Sökning matchar på druvor
    Givet att källaren innehåller följande viner:
      | namn    | druvor                          |
      | Barolo  | Nebbiolo                        |
      | Chablis | Pinot noir, chardonnay          |
      | Bourgogne | Chardonnay                    |
    När jag söker efter "pinot noir"
    Så ska vinlistan innehålla "Chablis"
    Och vinlistan ska inte innehålla "Barolo, Bourgogne"

  Scenario: Sökning matchar på tasting notes
    Givet att källaren innehåller följande viner:
      | namn    | tasting notes                |
      | Barolo  | Kraftfullt, toner av körsbär |
      | Chablis | Mineralisk och frisk         |
    När jag söker efter "körsbär"
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Chablis"

  Scenario: Sökning matchar på Systembolagets beskrivning
    Givet att källaren innehåller följande viner:
      | namn    | systembolagets beskrivning |
      | Barolo  | Nyanserad, kryddig smak    |
      | Chablis | Citrus och äpple           |
    När jag söker efter "kryddig"
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Chablis"

  Scenario: Sökning matchar på Munskänkarnas bedömning
    Givet att källaren innehåller följande viner:
      | namn    | munskänkarnas bedömning     |
      | Barolo  | Mer än prisvärt              |
      | Chablis | Något återhållen doft        |
    När jag söker efter "prisvärt"
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Chablis"

  Scenario: Sökning ignorerar diakritiska tecken
    Givet att källaren innehåller följande viner:
      | namn  | druvor   |
      | Rioja | Albariño |
      | Barolo | Nebbiolo |
    När jag söker efter "albarino"
    Så ska vinlistan innehålla "Rioja"
    Och vinlistan ska inte innehålla "Barolo"

  Scenario: Sökning kombineras med filter
    Givet att källaren innehåller följande viner:
      | namn      | vintyp | producent  |
      | Barolo    | Rött   | Pio Cesare |
      | Barbaresco| Vitt   | Pio Cesare |
    När jag söker efter "cesare" och filtrerar vinlistan på:
      | vintyp | Rött |
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Barbaresco"

  Scenario: Tom sökning visar alla viner
    Givet att källaren innehåller följande viner:
      | namn    |
      | Barolo  |
      | Chablis |
    När jag visar vinlistan utan filter
    Så ska vinlistan innehålla "Barolo, Chablis"
