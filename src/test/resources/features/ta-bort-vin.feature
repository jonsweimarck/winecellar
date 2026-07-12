# language: sv
Egenskap: Ta bort ett vin
  Som vinsamlare vill jag kunna ta bort ett vin ur källaren
  så att listan inte visar viner jag inte längre har

  Scenario: Ta bort ett vin ur källaren
    Givet att vinet "Barolo" finns i källaren
    När jag tar bort vinet "Barolo"
    Så ska källaren inte längre innehålla "Barolo"
