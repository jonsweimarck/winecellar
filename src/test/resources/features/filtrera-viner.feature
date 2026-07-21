# language: sv
Egenskap: Filtrera vinlistan
  Som vinsamlare vill jag kunna filtrera vinlistan på vintyp och ursprung
  (land, region, underregion), så att jag snabbt hittar en delmängd av
  en stor samling

  Scenario: Filtrera på en vintyp
    Givet att källaren innehåller följande viner:
      | namn    | vintyp |
      | Barolo  | Rött   |
      | Chablis | Vitt   |
    När jag filtrerar vinlistan på:
      | vintyp | Rött |
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Chablis"

  Scenario: Flera valda vintyper är en "eller"-filtrering inom samma facett
    Givet att källaren innehåller följande viner:
      | namn      | vintyp      |
      | Barolo    | Rött        |
      | Chablis   | Vitt        |
      | Champagne | Mousserande |
    När jag filtrerar vinlistan på:
      | vintyp | Rött, Vitt |
    Så ska vinlistan innehålla "Barolo, Chablis"
    Och vinlistan ska inte innehålla "Champagne"

  Scenario: Filtrera på land visar alla viner från landet oavsett region
    Givet att källaren innehåller följande viner:
      | namn    | land      | region   |
      | Barolo  | Italien   | Piemonte |
      | Chianti | Italien   | Toscana  |
      | Chablis | Frankrike |          |
    När jag filtrerar vinlistan på:
      | land | Italien |
    Så ska vinlistan innehålla "Barolo, Chianti"
    Och vinlistan ska inte innehålla "Chablis"

  Scenario: Filtrera på underregion visar bara den specifika underregionen
    Givet att källaren innehåller följande viner:
      | namn    | land    | region   | underregion |
      | Barolo  | Italien | Piemonte |             |
      | Langhe1 | Italien | Piemonte | Langhe      |
    När jag filtrerar vinlistan på:
      | underregion | Langhe |
    Så ska vinlistan innehålla "Langhe1"
    Och vinlistan ska inte innehålla "Barolo"

  Scenario: Filter på olika facetter kombineras med "och"-logik
    Givet att källaren innehåller följande viner:
      | namn    | vintyp | land      |
      | Barolo  | Rött   | Italien   |
      | Chianti | Rött   | Italien   |
      | Chablis | Vitt   | Frankrike |
    När jag filtrerar vinlistan på:
      | vintyp | Rött      |
      | land   | Frankrike |
    Så ska vinlistan vara tom

  Scenario: Utan filter visas alla viner
    Givet att källaren innehåller följande viner:
      | namn    |
      | Barolo  |
      | Chablis |
    När jag visar vinlistan utan filter
    Så ska vinlistan innehålla "Barolo, Chablis"
