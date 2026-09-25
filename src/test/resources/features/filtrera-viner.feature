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

  Scenario: Filtrera på minst ett visst antal flaskor
    Givet att källaren innehåller följande viner:
      | namn    | antal |
      | Barolo  | 3     |
      | Chablis | 0     |
    När jag filtrerar vinlistan på:
      | minAntalFlaskor | 1 |
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Chablis"

  Scenario: Tröskelvärdet är inkluderande, inte strikt "fler än"
    Givet att källaren innehåller följande viner:
      | namn    | antal |
      | Barolo  | 3     |
      | Chianti | 1     |
    När jag filtrerar vinlistan på:
      | minAntalFlaskor | 1 |
    Så ska vinlistan innehålla "Barolo, Chianti"

  Scenario: Ett tröskelvärde på 0 visar även utdruckna viner
    Givet att källaren innehåller följande viner:
      | namn    | antal |
      | Barolo  | 3     |
      | Chablis | 0     |
    När jag filtrerar vinlistan på:
      | minAntalFlaskor | 0 |
    Så ska vinlistan innehålla "Barolo, Chablis"

  # WINE-51: taggar är fri text (samma "normalisera inte i onödan"-princip
  # som land/druvor) - ett vin kan ha flera, och en tagg-facett filtrerar
  # precis som vintyp/land/region: ELLER inom facetten, OCH mot övriga.
  Scenario: Filtrera på en tagg
    Givet att källaren innehåller följande viner:
      | namn    | taggar           |
      | Barolo  | Favorit, Festvin |
      | Chablis | Vardag           |
    När jag filtrerar vinlistan på:
      | tagg | Favorit |
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Chablis"

  Scenario: Flera valda taggar är en "eller"-filtrering inom samma facett
    Givet att källaren innehåller följande viner:
      | namn      | taggar  |
      | Barolo    | Favorit |
      | Chablis   | Vardag  |
      | Champagne | Present |
    När jag filtrerar vinlistan på:
      | tagg | Favorit, Vardag |
    Så ska vinlistan innehålla "Barolo, Chablis"
    Och vinlistan ska inte innehålla "Champagne"

  Scenario: Taggfilter kombineras med andra facetter via "och"-logik
    Givet att källaren innehåller följande viner:
      | namn    | vintyp | taggar  |
      | Barolo  | Rött   | Favorit |
      | Chianti | Rött   | Vardag  |
      | Chablis | Vitt   | Favorit |
    När jag filtrerar vinlistan på:
      | vintyp | Rött    |
      | tagg   | Favorit |
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Chianti, Chablis"

  # WINE-51 (granskningsrunda 2): taggfiltret ska vara skiftlägesokänsligt,
  # precis som Wine.tags() (Wine.Builder.tags() lagrar alltid i en
  # CASE_INSENSITIVE_ORDER-TreeSet) och distinctTags()/filterpanelens
  # kryssrutor redan är - ett vin taggat med gemener ska matchas av ett
  # filter angivet med versaler, eller tvärtom.
  Scenario: Taggfiltret är skiftlägesokänsligt
    Givet att källaren innehåller följande viner:
      | namn    | taggar  |
      | Barolo  | favorit |
      | Chablis | vardag  |
    När jag filtrerar vinlistan på:
      | tagg | Favorit |
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Chablis"

  # WINE-56: AI-chattens "visa dessa viner i vinlistan"-länk filtrerar på
  # en exakt uppsättning vinnamn, inte en fritextsökning - samma
  # OCH-mellan-facetter/ELLER-inom-facetten-princip som taggar ovan.
  Scenario: Filtrera på en uppsättning vinnamn
    Givet att källaren innehåller följande viner:
      | namn      |
      | Barolo    |
      | Chablis   |
      | Champagne |
    När jag filtrerar vinlistan på:
      | vinnamn | Barolo, Champagne |
    Så ska vinlistan innehålla "Barolo, Champagne"
    Och vinlistan ska inte innehålla "Chablis"

  Scenario: Namnfilter kombineras med andra facetter via "och"-logik
    Givet att källaren innehåller följande viner:
      | namn    | vintyp |
      | Barolo  | Rött   |
      | Chianti | Vitt   |
    När jag filtrerar vinlistan på:
      | vintyp  | Rött           |
      | vinnamn | Barolo, Chianti |
    Så ska vinlistan innehålla "Barolo"
    Och vinlistan ska inte innehålla "Chianti"

  Scenario: Namnfiltret är skiftlägesokänsligt
    Givet att källaren innehåller följande viner:
      | namn   |
      | Barolo |
    När jag filtrerar vinlistan på:
      | vinnamn | barolo |
    Så ska vinlistan innehålla "Barolo"
