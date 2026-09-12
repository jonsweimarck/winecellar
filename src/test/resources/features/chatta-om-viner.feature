# language: sv
Egenskap: Chatta om sina viner
  Som vinsamlare vill jag kunna chatta med en AI-assistent om min vinkällare
  så att jag kan få hjälp och svar utan att själv leta igenom hela listan

  Scenario: Ställa en fråga i en ny konversation
    Givet att jag har följande viner i källaren:
      | namn   | vintyp | land    |
      | Barolo | Rött   | Italien |
    Och att chattassistenten svarar "Barolo passar bra till rödkött"
    När jag startar en ny konversation och frågar "Vilket vin passar till rödkött?"
    Så ska konversationen innehålla frågan "Vilket vin passar till rödkött?"
    Och ska konversationen innehålla svaret "Barolo passar bra till rödkött"

  Scenario: Fortsätta en befintlig konversation
    Givet att jag har en konversation med frågan "Vilket vin passar till rödkött?" och svaret "Barolo passar bra till rödkött"
    Och att chattassistenten svarar "Prova gärna en Chianti också"
    När jag frågar "Några fler förslag?" i samma konversation
    Så ska konversationen innehålla svaret "Prova gärna en Chianti också"

  Scenario: Max antal konversationer är nått
    Givet att jag redan har det maximala antalet konversationer
    När jag försöker starta en ny konversation
    Så ska jag få ett felmeddelande om att gränsen är nådd
    Och inga nya konversationer ska ha skapats

  Scenario: Max antal meddelanden i en konversation är nått
    Givet att jag har en konversation med det maximala antalet meddelanden
    När jag försöker skicka ytterligare ett meddelande i den konversationen
    Så ska jag få ett felmeddelande om att gränsen är nådd
    Och inga nya meddelanden ska ha lagts till i konversationen

  Scenario: Radera en konversation
    Givet att jag har en konversation med frågan "Vilket vin passar till rödkött?" och svaret "Barolo passar bra till rödkött"
    När jag raderar konversationen
    Så ska konversationen inte längre finnas
