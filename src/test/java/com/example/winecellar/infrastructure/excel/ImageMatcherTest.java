package com.example.winecellar.infrastructure.excel;

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
        ImageMatcher.Image image = matcher.findImage(null, "Barolo", null);

        assertThat(image).isNotNull();
        assertThat(image.data()).containsExactly(1, 2, 3);
        assertThat(image.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void skaReturneraNullOmIngenFilMatchar() throws Exception {
        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage(null, "Barolo", null)).isNull();
    }

    @Test
    void skaKännaIgenPngGifOchWebp() throws Exception {
        Files.write(imageFolder.resolve("Chablis.png"), new byte[] {1});
        Files.write(imageFolder.resolve("Rioja.gif"), new byte[] {1});
        Files.write(imageFolder.resolve("Cava.webp"), new byte[] {1});

        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage(null, "Chablis", null).mimeType()).isEqualTo("image/png");
        assertThat(matcher.findImage(null, "Rioja", null).mimeType()).isEqualTo("image/gif");
        assertThat(matcher.findImage(null, "Cava", null).mimeType()).isEqualTo("image/webp");
    }

    @Test
    void skaHoppaÖverFilerMedOkändÄndelse() throws Exception {
        Files.write(imageFolder.resolve("Anteckningar.txt"), new byte[] {1});

        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage(null, "Anteckningar", null)).isNull();
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

        assertThat(matcher.findImage(null, "Barolo", null)).isNull();
        assertThat(out.toString()).contains("Varning").contains("Barolo");
    }

    @Test
    void skaMatchaViaFullständigIdentitetNärProducentOchÅrgångÄrSatta() throws Exception {
        // Två viner som delar namn - bara identitetskonventionen kan skilja dem åt.
        Files.write(imageFolder.resolve("Pio_Cesare_Barolo_2018.jpg"), new byte[] {1});
        Files.write(imageFolder.resolve("Damilano_Barolo_2019.jpg"), new byte[] {2});

        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage("Pio Cesare", "Barolo", 2018).data()).containsExactly(1);
        assertThat(matcher.findImage("Damilano", "Barolo", 2019).data()).containsExactly(2);
    }

    @Test
    void skaFallaTillbakaTillNamnMatchningOmProducentEllerÅrgångSaknas() throws Exception {
        Files.write(imageFolder.resolve("Barolo.jpg"), new byte[] {3});

        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage(null, "Barolo", 2018).data()).containsExactly(3);
        assertThat(matcher.findImage("Pio Cesare", "Barolo", null).data()).containsExactly(3);
    }

    @Test
    void skaFallaTillbakaTillNamnMatchningOmFullständigIdentitetInteGerTräff() throws Exception {
        // Raden har full identitet, men bildfilen är fortfarande namngiven
        // enligt den äldre, namn-bara konventionen.
        Files.write(imageFolder.resolve("Barolo.jpg"), new byte[] {4});

        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage("Pio Cesare", "Barolo", 2018).data()).containsExactly(4);
    }

    @Test
    void identityFileNameStemSkaKombineraFältenMedUnderstreckOchErsättaMellanslag() {
        assertThat(ImageMatcher.identityFileNameStem("Pio Cesare", "Barolo", 2018))
                .isEqualTo("Pio_Cesare_Barolo_2018");
    }

    @Test
    void fileNameStemSkaAnvändaBaraNamnetNärProducentOchÅrgångSaknas() {
        assertThat(ImageMatcher.fileNameStem(null, "Barolo", null)).isEqualTo("Barolo");
    }

    @Test
    void fileNameStemSkaAnvändaProducentOchNamnNärÅrgångSaknas() {
        assertThat(ImageMatcher.fileNameStem("Pio Cesare", "Barolo", null))
                .isEqualTo("Pio_Cesare_Barolo");
    }

    @Test
    void fileNameStemSkaAnvändaNamnOchÅrgångNärProducentSaknas() {
        assertThat(ImageMatcher.fileNameStem(null, "Barolo", 2018)).isEqualTo("Barolo_2018");
    }

    @Test
    void fileNameStemSkaAnvändaAllaTreFältenNärDeÄrSatta() {
        assertThat(ImageMatcher.fileNameStem("Pio Cesare", "Barolo", 2018))
                .isEqualTo("Pio_Cesare_Barolo_2018");
    }

    @Test
    void skaMatchaBildMedProducentOchNamnNärÅrgångSaknas() throws Exception {
        Files.write(imageFolder.resolve("Pio_Cesare_Barolo.jpg"), new byte[] {5});

        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage("Pio Cesare", "Barolo", null).data()).containsExactly(5);
    }

    @Test
    void skaMatchaBildMedNamnOchÅrgångNärProducentSaknas() throws Exception {
        Files.write(imageFolder.resolve("Barolo_2018.jpg"), new byte[] {6});

        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage(null, "Barolo", 2018).data()).containsExactly(6);
    }

    @Test
    void skaFallaTillbakaTillNamnBaraMatchningNärPartiellIdentitetInteGerTräff() throws Exception {
        // Raden har producent + namn, men bildfilen följer den äldre namn-bara konventionen.
        Files.write(imageFolder.resolve("Barolo.jpg"), new byte[] {7});

        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage("Pio Cesare", "Barolo", null).data()).containsExactly(7);
    }

    @Test
    void skaSkiljaPåVinMedSammaNamnMenOlikaPartiellIdentitet() throws Exception {
        Files.write(imageFolder.resolve("Pio_Cesare_Barolo.jpg"), new byte[] {8});
        Files.write(imageFolder.resolve("Barolo_2018.jpg"), new byte[] {9});
        Files.write(imageFolder.resolve("Barolo.jpg"), new byte[] {10});

        ImageMatcher matcher = new ImageMatcher(imageFolder);

        assertThat(matcher.findImage("Pio Cesare", "Barolo", null).data()).containsExactly(8);
        assertThat(matcher.findImage(null, "Barolo", 2018).data()).containsExactly(9);
        assertThat(matcher.findImage(null, "Barolo", null).data()).containsExactly(10);
    }
}
