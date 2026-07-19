package com.example.winecellar.importexcel;

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
 * Matchar bildfiler i en mapp mot viner via filnamnet: en fil heter
 * exakt samma sak som {@link com.example.winecellar.domain.Wine#name()}
 * (bara filändelsen skiljer), t.ex. "Barolo.jpg" för ett vin med
 * name="Barolo". Om flera filer i mappen har samma filnamnsstam (t.ex.
 * "Barolo.jpg" och "Barolo.png") är det tvetydigt vilken som ska
 * användas - den stammen hoppas då över med en varning istället för att
 * gissa.
 */
final class Bildmatchare {

    private static final Map<String, String> MIME_PER_ÄNDELSE = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp"
    );

    private final Map<String, Path> filPerVinnamn;

    Bildmatchare(Path bildmapp) throws IOException {
        Map<String, List<Path>> kandidaterPerStam = new HashMap<>();
        try (Stream<Path> filer = Files.list(bildmapp)) {
            for (Path fil : filer.filter(Files::isRegularFile).toList()) {
                String filnamn = fil.getFileName().toString();
                int punktIndex = filnamn.lastIndexOf('.');
                if (punktIndex <= 0) {
                    continue;
                }
                String ändelse = filnamn.substring(punktIndex + 1).toLowerCase(Locale.ROOT);
                if (!MIME_PER_ÄNDELSE.containsKey(ändelse)) {
                    continue;
                }
                String stam = filnamn.substring(0, punktIndex);
                kandidaterPerStam.computeIfAbsent(stam, k -> new ArrayList<>()).add(fil);
            }
        }

        filPerVinnamn = new HashMap<>();
        for (Map.Entry<String, List<Path>> entry : kandidaterPerStam.entrySet()) {
            if (entry.getValue().size() > 1) {
                System.out.println("Varning: flera bildfiler heter \"" + entry.getKey()
                        + "\" (" + entry.getValue() + ") - hoppar över, tvetydigt vilken som ska användas.");
                continue;
            }
            filPerVinnamn.put(entry.getKey(), entry.getValue().get(0));
        }
    }

    /**
     * Null om ingen fil i mappen heter exakt {@code vinNamn} (bortsett
     * från filändelsen), eller om namnet var tvetydigt (se konstruktorn).
     */
    Bild hittaBild(String vinNamn) throws IOException {
        Path fil = filPerVinnamn.get(vinNamn);
        if (fil == null) {
            return null;
        }
        String filnamn = fil.getFileName().toString();
        String ändelse = filnamn.substring(filnamn.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return new Bild(Files.readAllBytes(fil), MIME_PER_ÄNDELSE.get(ändelse));
    }

    record Bild(byte[] data, String mimeType) {
    }
}
