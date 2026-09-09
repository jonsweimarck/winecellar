package com.example.winecellar.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * "Fail-safe, inte fail-insecure"-nät för {@code SPRING_PROFILES_ACTIVE=prod}
 * (WINE-43, kodgranskningsfynd, se ADR 0020/CLAUDE.md).
 *
 * <p>Till skillnad från {@code WINECELLAR_REMEMBER_ME_KEY} (vars uteblivna
 * värde gör "håll mig inloggad" avstängt, inte osäkert) lämnar en glömd
 * {@code prod}-profil sessionscookien osäker i produktion helt tyst - appen
 * startar och fungerar utåt sett normalt. Den här klassen bygger inget
 * säkerhetsnät i sig (profilen aktiveras inte automatiskt - en generell
 * inställning hade gjort lokal HTTP-utveckling obrukbar, se
 * {@code application-prod.yml}), bara en varningslogg vid uppstart om
 * driftmiljön känns igen (samma signal, {@code POSTGRESQL_ADDON_HOST}, som
 * {@code application.yml}s datasource-URL redan litar på för att skilja
 * Clever Cloud-drift från lokal utveckling/CI) men {@code prod}-profilen
 * ändå inte är aktiv.
 */
@Component
class ProdProfileGuard {

    private static final Logger log = LoggerFactory.getLogger(ProdProfileGuard.class);

    static final String CLEVER_CLOUD_SIGNAL_PROPERTY = "POSTGRESQL_ADDON_HOST";
    static final String PROD_PROFILE = "prod";

    private final Environment environment;

    ProdProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @EventListener
    void onApplicationReady(ApplicationReadyEvent event) {
        warnIfNeeded();
    }

    /**
     * Paketprivat (inte {@code private}) så testet kan anropa den direkt mot
     * en injicerad {@code Environment}-dubblett, utan att behöva boota en hel
     * applikationskontext bara för att verifiera loggraden.
     */
    void warnIfNeeded() {
        boolean looksLikeCleverCloud = environment.getProperty(CLEVER_CLOUD_SIGNAL_PROPERTY) != null;
        boolean prodProfileActive = List.of(environment.getActiveProfiles()).contains(PROD_PROFILE);
        if (looksLikeCleverCloud && !prodProfileActive) {
            log.warn("Appen ser ut att köra på Clever Cloud (miljövariabeln {} är satt), men "
                    + "\"{}\"-profilen är INTE aktiv - sessionscookien är då osäker i produktion "
                    + "(se ADR 0020 och CLAUDE.md, Kända fällor). Sätt miljövariabeln "
                    + "SPRING_PROFILES_ACTIVE={} i Clever Cloud-konsolen.",
                    CLEVER_CLOUD_SIGNAL_PROPERTY, PROD_PROFILE, PROD_PROFILE);
        }
    }
}
