package com.example.winecellar.application;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Medvetet LÖS validering av e-postadresser (WINE-59): hellre släppa igenom
 * en udda men korrekt adress än underkänna en riktig. Kräver bara en lokal
 * del, ett @ och en domändel med minst en punkt (inte först/sist och inte
 * dubbla punkter) - inga blanksteg. Plustecken, subdomäner, långa TLD:er och
 * icke-ASCII-tecken accepteras. Ingen RFC-regex. Den slutgiltiga kontrollen
 * av att adressen fungerar är verifieringsmailet.
 */
public final class EmailAddress {

    private static final int MAX_LENGTH = 254;
    private static final Pattern LOOSE = Pattern.compile("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$");

    private EmailAddress() {
    }

    /** @return adressen trimmad och i gemener, eller tom om den inte ser ut som en e-postadress */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH || !LOOSE.matcher(trimmed).matches()) {
            return Optional.empty();
        }
        return Optional.of(trimmed.toLowerCase(Locale.ROOT));
    }
}
