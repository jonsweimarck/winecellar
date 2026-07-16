# language: sv
Egenskap: Bilder för viner

  Scenario: Ladda upp en bild för ett vin
    Givet att vinet "Barolo" finns utan bild
    När jag laddar upp en bild av typen "image/jpeg" för vinet "Barolo"
    Så ska vinet "Barolo" ha en sparad bild av typen "image/jpeg"
