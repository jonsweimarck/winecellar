# 0015: Bulkimportens bilder skalas ned och konverteras till JPEG - ingen bit-exakt rundtripp

## Status

Accepted (2026-07-26)

## Context

WINE-24 (se [ADR 0014](0014-web-based-excel-import-export.md)) behövde
lösa ett verkligt tekniskt problem, upptäckt i en diskussion innan
kodningen: en bulkimport med upp till ~100 telefonfoton i EN
uppladdning krockar med både multipart-storleksgränsen (satt till 5MB,
dimensionerad för ETT foto) och risken att belasta serverns minne -
HTTP-sessionen här är Tomcats vanliga in-minnet-lagring, inget Spring
Session/Redis, så allt som läggs i den ligger kvar i JVM-heapen så
länge sessionen lever.

Lösningen var att skala ned varje bild i webbläsaren (Canvas) INNAN
uppladdning - samma teknik som etikettskanningen (WINE-5) redan
använde för ett enskilt foto, nu utökad till att köras över en hel
mappuppladdning (`import.html`). Nedskalningen skriver ALLTID om
bildens innehåll till JPEG (`canvas.toBlob(..., 'image/jpeg', 0.85)`),
oavsett ursprungsformat (PNG, GIF osv.), och begränsar längsta sidan
till 1600 pixlar.

## Decision

Bilder som går via bulkimporten (`/import`, `webkitdirectory`-
mappuppladdningen) konverteras ALLTID till en komprimerad JPEG och
skalas ned innan de når servern - en medveten, accepterad förlust av
bildfidelitet, inte en bugg. Konsekvensen som föranledde den här
ADR:n: en export (`/export/bilder.zip`, byte-identisk med det som en
gång laddades upp, se WINE-23) följt av en re-import via `/import` ger
INTE tillbaka samma bildbytes - bara en visuellt likvärdig,
omkomprimerad och eventuellt nedskalad JPEG-version.

Detta gäller BARA bulkimportens bildmapp. De två andra vägarna in för
en bild är opåverkade och förblir byte-exakta:

- Den vanliga "Etikett"-filuppladdningen i `vin-formular.html` (lägg
  till/redigera ETT vin i taget) - ingen klientsidans nedskalning
  alls, sparar originalfilens bytes rakt av.
- Etikettskanningens (WINE-5) egen nedskalning används bara för att
  skicka bilden till LLM:en för tolkning av textfält (namn, producent,
  årgång, land, region) - den nedskalade bilden sparas ALDRIG som
  vinets bild; ett eventuellt foto måste läggas till separat via det
  vanliga Etikett-fältet i samma formulär.

Alternativ som övervägdes och förkastades:

- **Höja multipart-gränserna ytterligare istället för att skala ned
  klientsidan.** Löser inte grundproblemet - JVM-minnet/sessionen
  belastas fortfarande av rådata, och gränsen skulle behöva vara
  orimligt hög för att tåla riktigt många okomprimerade foton.
- **Skala ned bilder serversidan istället för klientsidan.** Kräver
  att hela originalfilen ändå laddas upp först - samma multipart-/
  minnesproblem uppstår innan nedskalningen ens hinner köras.

## Consequences

- En bulkimporterad bild är ALDRIG bit-exakt densamma som originalet -
  bara visuellt likvärdig (nedskalad till max 1600px långsida,
  JPEG-komprimerad med kvalitet 0.85, se dock tillägget nedan för
  transparenta bilder). Om bit-exakt bildbevarande någonsin blir ett
  krav för bulkimporten specifikt är det en ny, separat avvägning
  (t.ex. ett kryssalternativ "skala inte ned" som skickar
  originalfilerna okomprimerade, med de multipart-/minneskonsekvenser
  det innebär).
- "Full rundtripp" för import/export-funktionen (se ADR 0014) gäller
  alltså textdata fullständigt, men bilder bara visuellt/praktiskt -
  inte bit-exakt - för just bulkvägen.
- `ImportExportFlowIT` (WINE-26) verifierar redan explicit att en
  bulkimporterad bild INTE är byte-identisk, bara att den kommer fram
  med korrekt `Content-Type` kopplad till rätt vin - ett medvetet
  testval som direkt speglar det här beslutet.

## Tillägg 2026-07-27 (WINE-29): transparens bevaras, JPEG bara för
## ogenomskinliga bilder

WINE-29 rapporterade att en bild med transparent bakgrund tappade sin
transparens vid bulkimport - en direkt, förutsägbar konsekvens av att
`import.html` **alltid** skrev om till JPEG oavsett ursprungsformat,
eftersom JPEG helt saknar stöd för alfakanal. Det ursprungliga beslutet
ovan vägde bara in filstorlek/minne, inte bildfidelitet för
transparenta bilder specifikt - det gapet var värt att täppa till,
utan att ge upp anledningen till att konvertera/komprimera
ogenomskinliga bilder över huvud taget.

**Justerat beslut:** efter nedskalningen på `<canvas>` läses
pixeldatan (`getImageData`) och alfakanalen skannas. Är bilden helt
ogenomskinlig (inget alfavärde < 255) är beteendet OFÖRÄNDRAT - JPEG,
kvalitet 0.85, exakt som innan. Har bilden minst en transparent/
halvtransparent pixel begärs istället `canvas.toBlob(callback,
'image/webp', 0.85)` - WebP stöder alfakanal och komprimerar
fortfarande bättre än PNG för de flesta bilder. HTML-specen kräver att
en webbläsare som inte kan koda WebP måste falla tillbaka till PNG
(aldrig till JPEG, som skulle återintroducera buggen, och aldrig
`null`) - `import.html` litar på detta och läser av den faktiska
returnerade blobbens `type` istället för att anta vilket format som
kom tillbaka, så filändelsen alltid stämmer med innehållet (samma
klass av bugg som WINE-26 redan fixade en gång, se `import.html`).
Servern behövde ingen ändring - `ImageMatcher` kände redan igen både
`webp` och `png` (`MIME_BY_EXTENSION`/`EXTENSION_BY_MIME`).

Detta är en förfining av samma beslut, inte en reversering - filstorlek
och minnesbegränsningen som motiverade nedskalning/komprimering från
början gäller fortfarande fullt ut, bara kodningsvalet är nu villkorat
på om bilden faktiskt har transparens.
