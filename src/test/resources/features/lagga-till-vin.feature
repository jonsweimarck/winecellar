# language: sv
Egenskap: Lägga till vin
  Som vinsamlare vill jag registrera ett nytt vin i källaren
  så att jag har koll på vad jag har liggande

  Scenario: Registrera ett nytt vin
    Givet att källaren är tom
    När jag lägger till ett vin med följande uppgifter:
      | namn      | Barolo     |
      | typ       | rött       |
      | producent | Pio Cesare |
      | land      | Italien    |
      | årgång    | 2018       |
      | flaskor   | 3          |
      | plats     | Låda 1     |
    Så ska källaren innehålla 1 vin
    Och vinet "Barolo" ska visas med 3 flaskor i "Låda 1"

  Scenario: Registrera ett vin med bara namnet ifyllt, för att fylla i resten senare
    Givet att källaren är tom
    När jag lägger till ett vin med bara namnet "Chianti Classico"
    Så ska källaren innehålla 1 vin
    Och vinet "Chianti Classico" ska sakna övriga uppgifter
