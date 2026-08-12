# language: sv
Egenskap: Import av Excel-fil
  Som vinsamlare vill jag få tydliga fel om en importfil innehåller
  rader som redan finns i min källare
  så att jag kan rätta filen istället för att appen gör en automatisk gissning

  Scenario: Dubbletter i importfilen rapporteras som fel
    Givet att vinet "Barolo" med producent "Pio Cesare" och årgång 2018 finns med 3 flaskor i källaren
    När jag förhandsgranskar en importfil med följande rader
      | rad | namn    | producent   | årgång |
      | 2   | Barolo  | Pio Cesare  | 2018   |
      | 3   | Barolo  |             |        |
    Så ska rad 2 rapporteras som en fullständig dubblett till ett befintligt vin
    Och ska rad 3 rapporteras som en möjlig dubblett till ett befintligt vin
    Och antalet nya viner i förhandsgranskningen ska vara 0
