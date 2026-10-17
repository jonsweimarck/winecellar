package com.example.winecellar.web;

import com.example.winecellar.application.StaleAccountCleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Kör {@link StaleAccountCleanup} vid en lyckad inloggning (WINE-65) - samma händelse och
 * mönster som {@link PendingImportCleanup} och {@link LastLoginRecorder}, ADR 0017. Får aldrig
 * kunna fälla en inloggning: alla fel fångas och loggas, och bara felets typ loggas
 * (aldrig dess meddelande, som i teorin kunde bära tokendata).
 */
@Component
class StaleAccountCleanupTrigger {

    private static final Logger log = LoggerFactory.getLogger(StaleAccountCleanupTrigger.class);

    private final StaleAccountCleanup cleanup;

    StaleAccountCleanupTrigger(StaleAccountCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @EventListener
    void onLogin(InteractiveAuthenticationSuccessEvent event) {
        try {
            cleanup.cleanup();
        } catch (RuntimeException e) {
            log.warn("Kunde inte städa gamla tokens/overifierade konton: {}", e.getClass().getName());
        }
    }
}
