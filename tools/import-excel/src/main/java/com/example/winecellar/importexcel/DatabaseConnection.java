package com.example.winecellar.importexcel;

/**
 * Delad av ImportExcel och ExportExcel - båda pratar med samma databas på
 * samma sätt (POSTGRESQL_ADDON_*-miljövariabler, samma konvention som
 * application.yml, annars localhost/winecellar). Extraherad hit när
 * ExportExcel tillkom (2026-07-22) - innan det fanns bara en anropsplats,
 * inte värt en egen klass då.
 */
final class DatabaseConnection {

    static String jdbcUrlFromEnvironment() {
        String host = environmentVariableOrDefault("POSTGRESQL_ADDON_HOST", "localhost");
        String port = environmentVariableOrDefault("POSTGRESQL_ADDON_PORT", "5432");
        String db = environmentVariableOrDefault("POSTGRESQL_ADDON_DB", "winecellar");
        return "jdbc:postgresql://" + host + ":" + port + "/" + db;
    }

    static String environmentVariableOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private DatabaseConnection() {
    }
}
