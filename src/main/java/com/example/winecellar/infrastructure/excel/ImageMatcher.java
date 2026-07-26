package com.example.winecellar.infrastructure.excel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Matchar bildfiler i en mapp mot viner via filnamnet. I första hand mot
 * den entydiga identitetskonventionen {@code <producent>_<namn>_<årgång>}
 * (samma identitetsfält som dubblettvarningen, se {@code Wine.
 * matchesIdentityOf}/WINE-6) - entydig även när flera viner delar namn,
 * till skillnad från den tidigare namn-bara matchningen. Faller tillbaka
 * till namn-bara matchning (en fil som heter exakt samma sak som vinets
 * namn, t.ex. "Barolo.jpg") när producent eller årgång saknas - annars
 * hade ofullständiga rader (ett medvetet stött scenario, se
 * ADR 0005/"Bara namnet obligatoriskt") blivit sämre betjänade än innan
 * (WINE-21, se ADR 0014).
 *
 * Ingen normalisering av filnamnet (mellanslag, specialtecken) - exakt
 * strängmatchning mot stammen, samma "exakt matchning, ingen gissning"-
 * princip som redan gällde för den namn-bara varianten.
 *
 * Om flera filer i mappen har samma filnamnsstam (t.ex. "Barolo.jpg" och
 * "Barolo.png") är det tvetydigt vilken som ska användas - den stammen
 * hoppas då över med en varning istället för att gissa. Gäller lika för
 * bägge konventionerna, eftersom båda bara är strängar i samma
 * uppslagstabell.
 */
public final class ImageMatcher {

    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp"
    );

    // Omvänd riktning av kartan ovan - en filändelse per MIME-typ (jpg
    // valt som kanonisk för image/jpeg, inte jpeg). Public sedan WINE-23,
    // som behöver den för att namnge bildfiler i zip-exporten (`web`-
    // paketet) - delad källa till sanning istället för att duplicera
    // MIME-kunskapen i ett eget uttryck där.
    public static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp"
    );

    private final Map<String, Path> fileByWineName;

    public ImageMatcher(Path imageFolder) throws IOException {
        Map<String, List<Path>> candidatesByStem = new HashMap<>();
        try (Stream<Path> files = Files.list(imageFolder)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String fileName = file.getFileName().toString();
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex <= 0) {
                    continue;
                }
                String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
                if (!MIME_BY_EXTENSION.containsKey(extension)) {
                    continue;
                }
                String stem = fileName.substring(0, dotIndex);
                candidatesByStem.computeIfAbsent(stem, k -> new ArrayList<>()).add(file);
            }
        }

        fileByWineName = new HashMap<>();
        for (Map.Entry<String, List<Path>> entry : candidatesByStem.entrySet()) {
            if (entry.getValue().size() > 1) {
                System.out.println("Varning: flera bildfiler heter \"" + entry.getKey()
                        + "\" (" + entry.getValue() + ") - hoppar över, tvetydigt vilken som ska användas.");
                continue;
            }
            fileByWineName.put(entry.getKey(), entry.getValue().get(0));
        }
    }

    /**
     * Försöker i första hand med den fullständiga identitetsstammen
     * ({@link #identityFileNameStem}, kräver att både `producer` och
     * `vintage` är satta) - faller tillbaka till namn-bara matchning
     * (mot `name`) om det inte gav träff, oavsett anledning (fältet
     * saknades, eller ingen fil i mappen råkade följa den konventionen).
     * Null om ingen av de två gav träff, eller om den träffade stammen
     * var tvetydig (se konstruktorn).
     */
    public Image findImage(String producer, String name, Integer vintage) throws IOException {
        if (producer != null && vintage != null) {
            Image match = findByStem(identityFileNameStem(producer, name, vintage));
            if (match != null) {
                return match;
            }
        }
        return findByStem(name);
    }

    private Image findByStem(String stem) throws IOException {
        Path file = fileByWineName.get(stem);
        if (file == null) {
            return null;
        }
        String fileName = file.getFileName().toString();
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return new Image(Files.readAllBytes(file), MIME_BY_EXTENSION.get(extension));
    }

    /**
     * Den delade namnkonventionen `<producent>_<namn>_<årgång>` - används
     * här för uppslag och (senare, WINE-23) för att namnge exporterade
     * bildfiler, så läs-/skrivsidan aldrig kan glida isär. Mellanslag
     * inom producent/namn ersätts med understreck (ett vanligt
     * filnamn ska inte innehålla mellanslag) - i övrigt ingen
     * normalisering (skiftläge/diakritiska tecken lämnas orörda, exakt
     * strängmatchning precis som den namn-bara varianten). Anroparen
     * ansvarar för att bara skicka in fält som faktiskt är satta (se
     * {@link #findImage}).
     */
    public static String identityFileNameStem(String producer, String name, int vintage) {
        return withoutSpaces(producer) + "_" + withoutSpaces(name) + "_" + vintage;
    }

    /**
     * Vilken stam ett vins bildfil ska få vid EXPORT (skrivsidan, WINE-23)
     * - till skillnad från {@link #findImage} (läsningen, som kan
     * acceptera BÅDA konventionerna liggande på disk) måste skrivsidan
     * committa till exakt EN stam per vin. Samma regel som läsningens
     * förstahandsval: fullständig identitet om både producer och vintage
     * är satta, annars bara namnet.
     */
    public static String fileNameStem(String producer, String name, Integer vintage) {
        return producer != null && vintage != null
                ? identityFileNameStem(producer, name, vintage)
                : name;
    }

    private static String withoutSpaces(String value) {
        return value.trim().replaceAll("\\s+", "_");
    }

    public record Image(byte[] data, String mimeType) {
    }
}
