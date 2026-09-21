package com.example.winecellar.infrastructure.excel;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Läser/skriver `Wine.tags` som en enda Excel-cell - en kommaseparerad
 * lista, standard CSV-citering för en tagg som själv innehåller ett
 * kommatecken eller citattecken (WINE-51). Ingen extern CSV-biblioteks-
 * dependency behövdes - reglerna är enkla nog (en delmängd av RFC 4180:
 * `,` som avgränsare, `"` som citattecken, dubblerat `""` för ett
 * citattecken inuti en citerad tagg) för en handskriven, litet hållen
 * implementation.
 *
 * En tagg som INTE behöver citeras skrivs rakt av; mellanslag runt en
 * ociterad tagg trimmas bort vid läsning (skrivningen lägger själv till
 * ett mellanslag efter varje separerande kommatecken, av läsbarhet i
 * kalkylarket).
 */
final class TagListCsv {

    private TagListCsv() {
    }

    /**
     * `null` för en tom tagglista - samma konvention som övriga
     * `WineRowWriter`-fält (cellen lämnas helt tom, inte en tom sträng).
     */
    static String format(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream().map(TagListCsv::quoteIfNeeded).collect(Collectors.joining(", "));
    }

    /**
     * `null`/blank text ger en tom mängd, aldrig `null` - matchar
     * `Wine.Builder.tags(...)`s egen normalisering.
     */
    static Set<String> parse(String text) {
        Set<String> tags = new TreeSet<>();
        if (text == null || text.isBlank()) {
            return tags;
        }
        int i = 0;
        int length = text.length();
        while (i < length) {
            while (i < length && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            if (i < length && text.charAt(i) == '"') {
                quoted = true;
                i++;
                while (i < length) {
                    char c = text.charAt(i);
                    if (c == '"') {
                        if (i + 1 < length && text.charAt(i + 1) == '"') {
                            field.append('"');
                            i += 2;
                        } else {
                            i++;
                            break;
                        }
                    } else {
                        field.append(c);
                        i++;
                    }
                }
                // Hoppa förbi eventuella tecken (t.ex. mellanslag) mellan det
                // avslutande citattecknet och nästa kommatecken.
                while (i < length && text.charAt(i) != ',') {
                    i++;
                }
            } else {
                while (i < length && text.charAt(i) != ',') {
                    field.append(text.charAt(i));
                    i++;
                }
            }
            if (i < length && text.charAt(i) == ',') {
                i++;
            }
            String value = quoted ? field.toString() : field.toString().trim();
            if (!value.isEmpty()) {
                tags.add(value);
            }
        }
        return tags;
    }

    private static String quoteIfNeeded(String tag) {
        boolean needsQuoting = tag.contains(",") || tag.contains("\"")
                || tag.contains("\n") || !tag.equals(tag.trim());
        if (!needsQuoting) {
            return tag;
        }
        return "\"" + tag.replace("\"", "\"\"") + "\"";
    }
}
