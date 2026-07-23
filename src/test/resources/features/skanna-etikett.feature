# language: sv
Egenskap: Lägga till vin genom att skanna en etikett
  Som vinsamlare vill jag fotografera en etikett och få ett ifyllt utkast
  så att jag slipper skriva in alla uppgifter för hand

  Scenario: Etiketten tolkas och ger ett ifyllt utkast
    Givet att etikettolkningen ger namn "Barolo", producent "Pio Cesare", årgång 2018, land "Italien" och region "Piemonte"
    När jag tolkar en fotograferad etikett
    Så visas ett utkast med namn "Barolo", producent "Pio Cesare", årgång 2018, land "Italien" och region "Piemonte"
    Och samtliga tolkade fält är markerade som tolkade

  Scenario: Endast namnet kunde tydas
    Givet att etikettolkningen bara ger namn "Chablis"
    När jag tolkar en fotograferad etikett
    Så visas ett utkast med bara namnet "Chablis" ifyllt
