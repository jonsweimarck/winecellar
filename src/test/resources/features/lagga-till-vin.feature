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

  # WINE-50: "eget betyg" är fri text (se ADR 0022) - sparas oförändrat vid
  # tillägg oavsett om texten matchar någon av munskänkarnas 29 etiketter
  # eller inte. Formulärets val mellan fritextfält och dropdown (se
  # WineControllerTest) styr bara hur fältet RENDERAS, aldrig hur det
  # sparas - det här scenariot testar den delen oberoende av formulärläge.
  Scenario: Registrera ett nytt vin med ett eget betyg som fri text
    Givet att källaren är tom
    När jag lägger till ett vin med följande uppgifter:
      | namn       | Barolo                             |
      | typ        | rött                                |
      | producent  | Pio Cesare                          |
      | land       | Italien                             |
      | årgång     | 2018                                |
      | flaskor    | 3                                   |
      | plats      | Låda 1                              |
      | eget betyg | Riktigt gott, testa igen om ett år |
    Så ska källaren innehålla 1 vin
    Och vinet "Barolo" ska ha eget betyg "Riktigt gott, testa igen om ett år"
