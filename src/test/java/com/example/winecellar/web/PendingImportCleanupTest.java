package com.example.winecellar.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testar städlogiken direkt mot en JUnit-{@code @TempDir} (inte den riktiga
 * OS-temp-katalogen) - ett vanligt `Instant` skickas in som "nu" istället för
 * att förlita sig på riktiga sömnar för att simulera en gammal mapp.
 */
class PendingImportCleanupTest {

    @Test
    void skaTaBortÖvergivenMappSomÄrÄldreÄnTröskeln(@TempDir Path tempRoot) throws IOException {
        Path gammalMapp = Files.createDirectory(tempRoot.resolve(ImportController.TEMP_DIR_PREFIX + "gammal"));
        // Filen måste skapas INNAN mappens ändringstid backas - att skriva en
        // fil i en katalog uppdaterar annars katalogens egen mtime igen,
        // vilket tyst hade upphävt simuleringen av en gammal mapp.
        Files.writeString(gammalMapp.resolve("data.xlsx"), "innehåll");
        Files.setLastModifiedTime(gammalMapp, FileTime.from(Instant.now().minus(Duration.ofHours(3))));

        new PendingImportCleanup(tempRoot).cleanupAbandonedImports(Instant.now());

        assertThat(Files.exists(gammalMapp)).isFalse();
    }

    @Test
    void skaLåtaFärskMappVaraKvar(@TempDir Path tempRoot) throws IOException {
        Path färskMapp = skapaMapp(tempRoot, ImportController.TEMP_DIR_PREFIX + "farsk", Duration.ofMinutes(10));

        new PendingImportCleanup(tempRoot).cleanupAbandonedImports(Instant.now());

        assertThat(Files.exists(färskMapp)).isTrue();
    }

    @Test
    void skaLåtaMappUtanRättPrefixVaraKvarOavsettÅlder(@TempDir Path tempRoot) throws IOException {
        Path annanMapp = skapaMapp(tempRoot, "någon-helt-annan-mapp", Duration.ofHours(5));

        new PendingImportCleanup(tempRoot).cleanupAbandonedImports(Instant.now());

        assertThat(Files.exists(annanMapp)).isTrue();
    }

    private Path skapaMapp(Path root, String namn, Duration ålder) throws IOException {
        Path mapp = Files.createDirectory(root.resolve(namn));
        Files.setLastModifiedTime(mapp, FileTime.from(Instant.now().minus(ålder)));
        return mapp;
    }
}
