# 0010: Excel-import/export som en fristående Maven-modul

## Status

Accepted (2026-07-17)

## Context

Den befintliga vinsamlingen fanns i en Excel-fil (`Vinlista.xlsx`) som
skulle importeras en gång, och en motsvarande exportmöjlighet önskades
senare för redigering/backup. Apache POI (för att läsa/skriva `.xlsx`)
är ett tungt beroende som bara behövs för dessa engångs-/verktygskörningar
- inte för den körande webbapplikationen.

## Decision

`tools/import-excel/` är en helt fristående Maven-modul: egen `pom.xml`,
**inte** ett `<module>` av rot-`pom.xml` (skulle tvinga rotens
packaging till "pom" och göra `clevercloud/maven.json`s
`spring-boot:run`-mål meningslöst). POI och JDBC-drivrutinen är
beroenden av den modulen, inte av den deployade jaren.

Modulen beror på huvudprojektets egen `com.example:winecellar`-artefakt
för att återanvända `Wine`/`WineType`/`Rating` (rena domänobjekt)
istället för att duplicera betygslistan och mappningslogiken. Roten
måste vara `mvn install`-ad lokalt innan importmodulen byggs.

`ImportExcel`/`ExportExcel` skriver/läser direkt via JDBC mot
`wines`-tabellen, inte via `WineService`/HTTP - fristående skript som
körs manuellt mot en redan existerande databas.

## Consequences

- Rotens `spring-boot-maven-plugin` fick `<classifier>exec</classifier>`
  - utan den skriver `repackage` (körs alltid före `install`) över den
  vanliga jaren med en Boot-fatjar, som inte går att bero på som ett
  vanligt Maven-bibliotek. Klassificeraren påverkar inte
  `spring-boot:run` (körs mot `target/classes`), så Clever
  Cloud-deployen är opåverkad.
- Ingen Gherkin-täckning för den här modulen (dess JDBC-integration
  testas inte av Cucumber-suiten) - verifiering av den fulla
  import-/exportflödet sker manuellt mot en riktig databas, medan
  radmappningslogiken (`VinradParser`/`VinradSkrivare`) har vanliga
  JUnit-enhetstester.
- Kolumnlayouten (A-V) delas mellan `VinradParser` (läsning) och
  `VinradSkrivare` (skrivning) via paketsynliga `COL_*`-konstanter i
  `VinradParser` - en delad källa till sanning istället för att
  duplicera kolumnindexen i två klasser.
- Verktygen körs lokalt och pratar med Postgres-tillägget över
  nätverket när de riktas mot produktion - Clever Cloud har inget
  CLI/konsol att köra verktyget *på*, men det behövs inte heller.
