package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Registrerar tidpunkten för senaste lyckade inloggning (WINE-62). Lyssnar
 * på samma händelse som {@link PendingImportCleanup}: Spring Security
 * publicerar den både vid formulärinloggning och när en
 * "håll mig inloggad"-cookie loggar in en användare på nytt. Registreringens
 * manuella auto-inloggning publicerar den INTE - där sätts senaste login
 * i stället till kontots skapandetid (se RegistrationService). En
 * misslyckad inloggning ger ingen sådan händelse och ändrar alltså inget.
 *
 * Får aldrig kunna fälla en inloggning: alla fel fångas och loggas.
 */
@Component
class LastLoginRecorder {

    private static final Logger log = LoggerFactory.getLogger(LastLoginRecorder.class);

    private final UserRepository userRepository;

    LastLoginRecorder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @EventListener
    void onLogin(InteractiveAuthenticationSuccessEvent event) {
        try {
            String username = event.getAuthentication().getName();
            userRepository.findByUsername(username)
                    .ifPresent(user -> userRepository.updateLastLogin(user.id(), Instant.now()));
        } catch (RuntimeException e) {
            log.warn("Kunde inte registrera senaste inloggning", e);
        }
    }
}
