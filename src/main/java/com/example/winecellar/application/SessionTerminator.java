package com.example.winecellar.application;

/**
 * Port: avsluta alla pågående inloggade sessioner för en användare
 * (WINE-59: efter ett lösenordsbyte). Implementeras i webblagret mot
 * Spring Securitys SessionRegistry.
 */
public interface SessionTerminator {

    void terminateSessionsOf(String username);
}
