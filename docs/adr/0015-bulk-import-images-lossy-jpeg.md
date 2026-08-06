# 0015: Bulkimportens bilder skalas ned och komprimeras - ingen bit-exakt rundtripp

## Status

Accepted (2026-07-26)

## Context

WINE-24 (se [ADR 0014](0014-web-based-excel-import-export.md)) behövde
lösa ett verkligt tekniskt problem, upptäckt i en diskussion innan
kodningen: en bulkimport med upp till ~100 telefonfoton i EN
uppladdning krockar med både uppladdningens storleksgräns (satt för
ett enda foto) och risken att belasta serverns minne - den tillfälliga
lagringen mellan förfrågningar under en pågående inloggning ligger kvar
i serverns minne så länge sessionen lever.

Lösningen var att skala ned varje bild i webbläsaren INNAN uppladdning
- samma teknik som etikettskanningen (WINE-5) redan använde för ett
enskilt foto, nu utökad till att köras över en hel mappuppladdning.
Nedskalningen skriver ALLTID om bildens innehåll till ett komprimerat
format, oavsett ursprungsformat, och begränsar bildens längsta sida.

## Decision

Bilder som går via bulkimporten konverteras ALLTID till ett
komprimerat, nedskalat format innan de når servern - en medveten,
accepterad förlust av bildfidelitet, inte en bugg. Konsekvensen som
föranledde den här ADR:n: en export (byte-identisk med det som en
gång laddades upp, se WINE-23) följt av en re-import ger INTE tillbaka
samma bildbytes - bara en visuellt likvärdig, omkomprimerad och
eventuellt nedskalad version.

Detta gäller BARA bulkimportens bildmapp. De två andra vägarna in för
en bild är opåverkade och förblir byte-exakta:

- Den vanliga bilduppladdningen för ETT vin i taget (lägg
  till/redigera) - ingen klientsidans nedskalning alls, sparar
  originalfilens bytes rakt av.
- Etikettskanningens (WINE-5) egen nedskalning används bara för att
  skicka bilden för tolkning av textfält (namn, producent, årgång,
  land, region) - den nedskalade bilden sparas ALDRIG som vinets bild;
  ett eventuellt foto måste läggas till separat via det vanliga
  bilduppladdningsfältet i samma formulär.

Alternativ som övervägdes och förkastades:

- **Höja uppladdningens storleksgränser ytterligare istället för att
  skala ned klientsidan.** Löser inte grundproblemet - serverns minne
  belastas fortfarande av rådata, och gränsen skulle behöva vara
  orimligt hög för att tåla riktigt många okomprimerade foton.
- **Skala ned bilder på servern istället för i webbläsaren.** Kräver
  att hela originalfilen ändå laddas upp först - samma
  storleks-/minnesproblem uppstår innan nedskalningen ens hinner
  köras.

## Consequences

- En bulkimporterad bild är ALDRIG bit-exakt densamma som originalet -
  bara visuellt likvärdig (se dock tillägget nedan för transparenta
  bilder). Om bit-exakt bildbevarande någonsin blir ett krav för
  bulkimporten specifikt är det en ny, separat avvägning (t.ex. ett
  kryssalternativ "skala inte ned" som skickar originalfilerna
  okomprimerade, med de storleks-/minneskonsekvenser det innebär).
- "Full rundtripp" för import/export-funktionen (se ADR 0014) gäller
  alltså textdata fullständigt, men bilder bara visuellt/praktiskt -
  inte bit-exakt - för just bulkvägen.
- Ett acceptanstest (WINE-26) verifierar redan explicit att en
  bulkimporterad bild INTE är byte-identisk, bara att den kommer fram
  korrekt kopplad till rätt vin - ett medvetet testval som direkt
  speglar det här beslutet.

## Tillägg 2026-07-27 (WINE-29): transparens bevaras, förlustfullt format bara för ogenomskinliga bilder

WINE-29 rapporterade att en bild med transparent bakgrund tappade sin
transparens vid bulkimport - en direkt, förutsägbar konsekvens av att
nedskalningen **alltid** skrev om till ett format utan stöd för
alfakanal, oavsett ursprungsformat. Det ursprungliga beslutet ovan
vägde bara in filstorlek/minne, inte bildfidelitet för transparenta
bilder specifikt - det gapet var värt att täppa till, utan att ge upp
anledningen till att konvertera/komprimera ogenomskinliga bilder över
huvud taget.

**Justerat beslut:** efter nedskalningen läses bildens pixeldata och
alfakanalen skannas. Är bilden helt ogenomskinlig är beteendet
OFÖRÄNDRAT - exakt som innan. Har bilden minst en transparent/
halvtransparent pixel används istället ett format som stöder alfakanal
och som fortfarande komprimerar bättre än ett okomprimerat alternativ
för de flesta bilder. Webbläsarstandarden kräver att en webbläsare som
inte kan koda det formatet måste falla tillbaka till ett annat
alfakapabelt format (aldrig till det ursprungliga förlustfulla
formatet, som skulle återintroducera buggen, och aldrig ingenting
alls) - lösningen litar på detta och läser av det faktiskt returnerade
resultatets typ istället för att anta vilket format som kom tillbaka,
så filändelsen alltid stämmer med innehållet (samma klass av bugg som
WINE-26 redan fixade en gång). Servern behövde ingen ändring -
bildmatchningen kände redan igen båda de aktuella formaten.

Detta är en förfining av samma beslut, inte en reversering -
filstorlek och minnesbegränsningen som motiverade nedskalning/
komprimering från början gäller fortfarande fullt ut, bara
kodningsvalet är nu villkorat på om bilden faktiskt har transparens.
