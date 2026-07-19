package com.example.winecellar.importexcel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BildmatchareTest {

    @TempDir
    private Path bildmapp;

    @Test
    void skaHittaBildVarsFilnamnMatcharVinnamnetExakt() throws Exception {
        Files.write(bildmapp.resolve("Barolo.jpg"), new byte[] {1, 2, 3});

        Bildmatchare matchare = new Bildmatchare(bildmapp);
        Bildmatchare.Bild bild = matchare.hittaBild("Barolo");

        assertThat(bild).isNotNull();
        assertThat(bild.data()).containsExactly(1, 2, 3);
        assertThat(bild.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void skaReturneraNullOmIngenFilMatchar() throws Exception {
        Bildmatchare matchare = new Bildmatchare(bildmapp);

        assertThat(matchare.hittaBild("Barolo")).isNull();
    }

    @Test
    void skaKännaIgenPngGifOchWebp() throws Exception {
        Files.write(bildmapp.resolve("Chablis.png"), new byte[] {1});
        Files.write(bildmapp.resolve("Rioja.gif"), new byte[] {1});
        Files.write(bildmapp.resolve("Cava.webp"), new byte[] {1});

        Bildmatchare matchare = new Bildmatchare(bildmapp);

        assertThat(matchare.hittaBild("Chablis").mimeType()).isEqualTo("image/png");
        assertThat(matchare.hittaBild("Rioja").mimeType()).isEqualTo("image/gif");
        assertThat(matchare.hittaBild("Cava").mimeType()).isEqualTo("image/webp");
    }

    @Test
    void skaHoppaÖverFilerMedOkändÄndelse() throws Exception {
        Files.write(bildmapp.resolve("Anteckningar.txt"), new byte[] {1});

        Bildmatchare matchare = new Bildmatchare(bildmapp);

        assertThat(matchare.hittaBild("Anteckningar")).isNull();
    }

    @Test
    void skaHoppaÖverOchVarnaVidTvetydigtNamn() throws Exception {
        Files.write(bildmapp.resolve("Barolo.jpg"), new byte[] {1});
        Files.write(bildmapp.resolve("Barolo.png"), new byte[] {2});

        PrintStream ursprungligUtström = System.out;
        ByteArrayOutputStream utström = new ByteArrayOutputStream();
        System.setOut(new PrintStream(utström));
        Bildmatchare matchare;
        try {
            matchare = new Bildmatchare(bildmapp);
        } finally {
            System.setOut(ursprungligUtström);
        }

        assertThat(matchare.hittaBild("Barolo")).isNull();
        assertThat(utström.toString()).contains("Varning").contains("Barolo");
    }
}
