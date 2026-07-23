package com.example.winecellar.importexcel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImageMatcherTest {

    @TempDir
    private Path imageFolder;

    @Test
    void skaHittaBildVarsFilnamnMatcharVinnamnetExakt() throws Exception {
        Files.write(imageFolder.resolve("Barolo.jpg"), new byte[] {1, 2, 3});

        ImageMatcher matcher = new ImageMatcher(imageFolder);
        ImageMatcher.Image image = matcher.findImage("Barolo");

        assertThat(image).isNotNull();
        assertThat(image.data()).containsExactly(1, 2, 3);
        assertThat(image.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void skaReturneraNullOmIngenFilMatchar() throws Exception {
        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage("Barolo")).isNull();
    }

    @Test
    void skaKännaIgenPngGifOchWebp() throws Exception {
        Files.write(imageFolder.resolve("Chablis.png"), new byte[] {1});
        Files.write(imageFolder.resolve("Rioja.gif"), new byte[] {1});
        Files.write(imageFolder.resolve("Cava.webp"), new byte[] {1});

        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage("Chablis").mimeType()).isEqualTo("image/png");
        assertThat(matcher.findImage("Rioja").mimeType()).isEqualTo("image/gif");
        assertThat(matcher.findImage("Cava").mimeType()).isEqualTo("image/webp");
    }

    @Test
    void skaHoppaÖverFilerMedOkändÄndelse() throws Exception {
        Files.write(imageFolder.resolve("Anteckningar.txt"), new byte[] {1});

        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage("Anteckningar")).isNull();
    }

    @Test
    void skaHoppaÖverOchVarnaVidTvetydigtNamn() throws Exception {
        Files.write(imageFolder.resolve("Barolo.jpg"), new byte[] {1});
        Files.write(imageFolder.resolve("Barolo.png"), new byte[] {2});

        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        ImageMatcher matcher;
        try {
            matcher = new ImageMatcher(imageFolder);
        } finally {
            System.setOut(originalOut);
        }

        assertThat(matcher.findImage("Barolo")).isNull();
        assertThat(out.toString()).contains("Varning").contains("Barolo");
    }
}
