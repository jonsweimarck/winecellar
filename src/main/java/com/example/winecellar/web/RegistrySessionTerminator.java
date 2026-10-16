package com.example.winecellar.web;

import com.example.winecellar.application.SessionTerminator;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Avslutar en användares pågående sessioner via SessionRegistry (WINE-59,
 * efter ett lösenordsbyte). Samma mekanism som AdminController använder för
 * en raderad användare. Registret är i minnet - passar enkelinstansdrift.
 */
@Component
class RegistrySessionTerminator implements SessionTerminator {

    private final SessionRegistry sessionRegistry;

    RegistrySessionTerminator(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void terminateSessionsOf(String username) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String name = principal instanceof UserDetails details ? details.getUsername() : principal.toString();
            if (name.equalsIgnoreCase(username)) {
                sessionRegistry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
            }
        }
    }
}
