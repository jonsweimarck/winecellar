# language: sv
Egenskap: Registrera ett nytt konto
  Som ny besökare vill jag kunna skapa ett eget konto
  så att jag kan använda vinkällaren

  # "Jag är inloggad direkt efter registrering" och "min vinlista är tom"
  # (ursprungliga WINE-11-scenarier) testas i RegistrationControllerTest
  # (webblagret, verifierar sessionen) - "min vinlista är tom" gäller
  # egentligen inte förrän WINE-13 scopar listan per användare; fram
  # tills dess delar alla inloggade användare samma lista, se
  # RegistrationController/CLAUDE.md.

  Scenario: Registrera ett nytt konto
    Givet att inget konto med användarnamnet "vinälskare" finns
    När jag registrerar mig med användarnamnet "vinälskare" och lösenordet "hemligt123"
    Så skapas ett konto med användarnamnet "vinälskare"

  Scenario: Användarnamnet är redan upptaget
    Givet att ett konto med användarnamnet "vinälskare" redan finns
    När jag försöker registrera mig med användarnamnet "vinälskare"
    Så nekas registreringen på grund av att användarnamnet är upptaget
