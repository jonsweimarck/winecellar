# language: sv
Egenskap: Varning vid misstänkta dubbletter
  Som vinsamlare vill jag bli varnad om jag försöker lägga till ett vin
  som redan finns i källaren
  så att jag inte råkar registrera samma vin flera gånger av misstag

  Scenario: Fullständig matchning - ökar antalet på det befintliga vinet
    Givet att vinet "Barolo" med producent "Pio Cesare" och årgång 2018 finns med 3 flaskor
    När jag försöker lägga till ett vin med namn "Barolo", producent "Pio Cesare" och årgång 2018
    Så ska appen upptäcka en fullständig dubblett av "Barolo"
    När jag väljer att öka antalet på den befintliga dubbletten
    Så ska vinet "Barolo" nu ha 4 flaskor
    Och källaren ska innehålla totalt 1 vin

  Scenario: Fullständig matchning - namn/producent skrivna med annat skiftläge räknas ändå som samma vin
    Givet att vinet "Barolo" med producent "Pio Cesare" och årgång 2018 finns med 3 flaskor
    När jag försöker lägga till ett vin med namn "barolo", producent "PIO CESARE" och årgång 2018
    Så ska appen upptäcka en fullständig dubblett av "Barolo"
    Och källaren ska innehålla totalt 1 vin

  Scenario: Fullständig matchning - redigerar ett id-fält och lägger till som nytt
    Givet att vinet "Barolo" med producent "Pio Cesare" och årgång 2018 finns med 3 flaskor
    När jag försöker lägga till ett vin med namn "Barolo", producent "Pio Cesare" och årgång 2018
    Så ska appen upptäcka en fullständig dubblett av "Barolo"
    När jag ändrar årgången till 2019 och försöker lägga till vinet igen
    Så källaren ska innehålla totalt 2 viner

  Scenario: Delvis matchning - ökar antalet på det befintliga vinet
    Givet att vinet "Barolo" med producent "Pio Cesare" och årgång 2018 finns med 3 flaskor
    När jag försöker lägga till ett vin med bara namnet "Barolo"
    Så ska appen upptäcka en möjlig dubblett av "Barolo"
    När jag väljer att öka antalet på den möjliga dubbletten
    Så ska vinet "Barolo" nu ha 4 flaskor
    Och källaren ska innehålla totalt 1 vin

  Scenario: Delvis matchning - lägger till som nytt vin ändå
    Givet att vinet "Barolo" med producent "Pio Cesare" och årgång 2018 finns med 3 flaskor
    När jag försöker lägga till ett vin med bara namnet "Barolo"
    Så ska appen upptäcka en möjlig dubblett av "Barolo"
    När jag väljer att lägga till vinet som nytt ändå
    Så källaren ska innehålla totalt 2 viner

  Scenario: Inget id-fält matchar - ingen dubblettvarning
    Givet att vinet "Barolo" med producent "Pio Cesare" och årgång 2018 finns med 3 flaskor
    När jag försöker lägga till ett vin med namn "Chablis", producent "Domaine X" och årgång 2020
    Så ska appen inte upptäcka någon dubblett
    Och källaren ska innehålla totalt 2 viner
