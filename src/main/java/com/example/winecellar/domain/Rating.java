package com.example.winecellar.domain;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * De 29 fasta betygsvärdena från källfilens (Vinlista.xlsx) Listor-flik,
 * delade av own_rating och munskankarna_rating (se CLAUDE.md). Java-
 * konstantnamnen är korta (R16, R14_5 osv.) - samma mönster som WineType
 * (RED/WHITE...), inte den fullständiga svenska etiketten - eftersom
 * @Enumerated(EnumType.STRING) lagrar konstantens namn och Hibernate
 * genererar CHECK-constrainten från just de namnen.
 */
public enum Rating {
    R20("20 (18 - 20 Exceptionellt vin)"),
    R19_5("19,5 (18 - 20 Exceptionellt vin)"),
    R19("19 (18 - 20 Exceptionellt vin)"),
    R18_5("18,5 (18 - 20 Exceptionellt vin)"),
    R18("18 (18 - 20 Exceptionellt vin)"),
    R17_5("17,5 (15 - 17,5 Högklassigt vin)"),
    R17("17 (15 - 17,5 Högklassigt vin)"),
    R16_5("16,5 (15 - 17,5 Högklassigt vin)"),
    R16("16 (15 - 17,5 Högklassigt vin)"),
    R15_5("15,5 (15 - 17,5 Högklassigt vin)"),
    R15("15 (15 - 17,5 Högklassigt vin)"),
    R14_5("14,5 (12 - 14,5 Bra till mycket bra vin)"),
    R14("14 (12 - 14,5 Bra till mycket bra vin)"),
    R13_5("13,5 (12 - 14,5 Bra till mycket bra vin)"),
    R13("13 (12 - 14,5 Bra till mycket bra vin)"),
    R12_5("12,5 (12 - 14,5 Bra till mycket bra vin)"),
    R12("12 (12 - 14,5 Bra till mycket bra vin)"),
    R11_5("11,5 (9 - 11,5 Medelbra vin)"),
    R11("11 (9 - 11,5 Medelbra vin)"),
    R10_5("10,5 (9 - 11,5 Medelbra vin)"),
    R10("10 (9 - 11,5 Medelbra vin)"),
    R9_5("9,5 (9 - 11,5 Medelbra vin)"),
    R9("9 (9 - 11,5 Medelbra vin)"),
    R8_5("8,5 (6 - 8,5 Enkel vin)"),
    R8("8 (6 - 8,5 Enkel vin)"),
    R7_5("7,5 (6 - 8,5 Enkel vin)"),
    R7("7 (6 - 8,5 Enkel vin)"),
    R6_5("6,5 (6 - 8,5 Enkel vin)"),
    R6("6 (6 - 8,5 Enkel vin)");

    private final String label;

    Rating(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    private static final Map<String, Rating> BY_NORMALIZED_LABEL = Stream.of(values())
            .collect(Collectors.toMap(r -> normalize(r.label), r -> r));

    /**
     * Källfilens etiketter har inkonsekvent mellanslag (t.ex. dubbla
     * mellanslag i några av "Enkel vin"-raderna) - uppenbara inmatningsfel,
     * inte meningsfulla skillnader. Both etiketten här och indata
     * normaliseras (mellanslag kollapsas) innan de jämförs.
     */
    public static Rating fromLabel(String label) {
        Rating found = BY_NORMALIZED_LABEL.get(normalize(label));
        if (found == null) {
            throw new IllegalArgumentException("Okänt betyg i indata: \"" + label + "\"");
        }
        return found;
    }

    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.of("sv", "SE"));
    }
}
