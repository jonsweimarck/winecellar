package com.example.winecellar.importexcel;

/**
 * Delad av ImportExcel och ExportExcel - båda pratar med samma databas på
 * samma sätt (POSTGRESQL_ADDON_*-miljövariabler, samma konvention som
 * application.yml, annars localhost/winecellar). Extraherad hit när
 * ExportExcel tillkom (2026-07-22) - innan det fanns bara en anropsplats,
 * inte värt en egen klass då.
 */
final class Databaskoppling {

    static String jdbcUrlFrånMiljö() {
        String host = miljövariabelEllerStandard("POSTGRESQL_ADDON_HOST", "localhost");
        String port = miljövariabelEllerStandard("POSTGRESQL_ADDON_PORT", "5432");
        String db = miljövariabelEllerStandard("POSTGRESQL_ADDON_DB", "winecellar");
        return "jdbc:postgresql://" + host + ":" + port + "/" + db;
    }

    static String miljövariabelEllerStandard(String namn, String standardvärde) {
        String värde = System.getenv(namn);
        return värde == null || värde.isBlank() ? standardvärde : värde;
    }

    private Databaskoppling() {
    }
}
