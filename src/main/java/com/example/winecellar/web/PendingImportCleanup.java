package com.example.winecellar.web;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

/**
 * Städar övergivna temp-mappar som {@code ImportController.stashUploadForCommit}
 * (WINE-24) skapar - en användare som aldrig bekräftar sin import (stänger
 * fliken, sessionen går ut) lämnar annars mappen kvar på disk för alltid.
 * Triggas av en lyckad inloggning istället för ett eget schema - se
 * ADR 0017 för den fulla motiveringen och dess konsekvenser (bland annat
 * att WINE-11:s auto-inloggning vid registrering INTE triggar detta).
 */
@Component
class PendingImportCleanup {

    private static final Duration MAX_AGE = Duration.ofHours(2);

    private final Path tempRoot;

    PendingImportCleanup() {
        this(Path.of(System.getProperty("java.io.tmpdir")));
    }

    PendingImportCleanup(Path tempRoot) {
        this.tempRoot = tempRoot;
    }

    @EventListener
    void onLogin(InteractiveAuthenticationSuccessEvent event) {
        cleanupAbandonedImports(Instant.now());
    }

    /**
     * `now` skickas in explicit (inte `Instant.now()` internt) så tester kan
     * styra tröskeln exakt mot en mapps satta ändringstid, utan att förlita
     * sig på riktiga sömnar.
     */
    void cleanupAbandonedImports(Instant now) {
        Instant threshold = now.minus(MAX_AGE);
        try (var entries = Files.list(tempRoot)) {
            entries
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(ImportController.TEMP_DIR_PREFIX))
                    .filter(path -> isOlderThan(path, threshold))
                    .forEach(this::deleteRecursivelyQuietly);
        } catch (IOException e) {
            // Best effort - ett fel här (t.ex. temp-katalogen tillfälligt
            // otillgänglig) ska aldrig krascha en inloggning. Nästa
            // inloggning försöker igen.
        }
    }

    private boolean isOlderThan(Path path, Instant threshold) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(threshold);
        } catch (IOException e) {
            return false;
        }
    }

    private void deleteRecursivelyQuietly(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    // Best effort - se klasskommentar.
                }
            });
        } catch (IOException e) {
            // Best effort - se klasskommentar.
        }
    }
}
