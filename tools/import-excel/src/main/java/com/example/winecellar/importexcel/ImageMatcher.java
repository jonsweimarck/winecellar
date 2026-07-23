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
final class ImageMatcher {

    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp"
    );

    // Omvänd riktning av kartan ovan, för ExportExcel som skriver
    // bildfiler till samma mapp - en filändelse per MIME-typ (jpg valt
    // som kanonisk för image/jpeg, inte jpeg). Paketsynlig, delad källa
    // till sanning istället för att duplicera MIME-kunskapen i ett eget
    // uttryck i ExportExcel (byggt 2026-07-22).
    static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp"
    );

    private final Map<String, Path> fileByWineName;

    ImageMatcher(Path imageFolder) throws IOException {
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
     * Null om ingen fil i mappen heter exakt {@code wineName} (bortsett
     * från filändelsen), eller om namnet var tvetydigt (se konstruktorn).
     */
    Image findImage(String wineName) throws IOException {
        Path file = fileByWineName.get(wineName);
        if (file == null) {
            return null;
        }
        String fileName = file.getFileName().toString();
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return new Image(Files.readAllBytes(file), MIME_BY_EXTENSION.get(extension));
    }

    record Image(byte[] data, String mimeType) {
    }
}
