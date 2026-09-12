# language: sv
Egenskap: Konversationer överlever en omstart
  Som vinsamlare vill jag att mina chattkonversationer sparas varaktigt
  så att de inte försvinner om applikationen startas om

  Scenario: En konversation överlever en omstart av applikationen
    Givet att jag har sparat en konversation med frågan "Vilket vin passar till fisk?" och svaret "Prova en Chablis"
    När applikationen startas om
    Så ska konversationen fortfarande innehålla frågan "Vilket vin passar till fisk?" och svaret "Prova en Chablis"
