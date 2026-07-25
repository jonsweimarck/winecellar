# language: sv
Egenskap: Dataisolering mellan användare
  Som vinsamlare vill jag att min vinlista är privat
  så att andra användare inte kan se eller ändra mina viner

  Scenario: Ett vin syns inte för en annan användare
    Givet att användaren "alice" har lagt till vinet "Barolo"
    Och att användaren "bob" är inloggad
    När "bob" öppnar sin vinlista
    Så syns inte "Barolo" i listan
