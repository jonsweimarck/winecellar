# language: sv
Egenskap: Sökning mot en riktig databas
  Som vinsamlare vill jag att fritextsökningen fungerar mot den riktiga
  databasen (Postgres via JpaWineRepositorys native query), inte bara mot
  testdubbletten InMemoryWineRepository som soka-viner.feature kör mot -
  se CLAUDE.md om owner_id-kolumnen (WINE-10) som en gång saknades i
  native-queryns kolumnlista och kraschade sökningen i produktion utan
  att något test fångade det.

  Scenario: Sökning hittar ett vin på dess druvor
    Givet att vinet "Barolo" med druvan "Nebbiolo" är sparat i källaren
    När jag söker efter "nebbiolo" mot databasen
    Så ska vinet "Barolo" finnas i sökresultatet
