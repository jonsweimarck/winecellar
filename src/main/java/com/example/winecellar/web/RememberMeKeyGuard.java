package com.example.winecellar.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * "Fail-safe, inte tyst"-nät för {@code WINECELLAR_REMEMBER_ME_KEY} (WINE-47,
 * upptäckt i produktion: nyckeln saknades i Clever Cloud-konsolen, vilket
 * gjorde "håll mig inloggad" helt overksam utan att något syntes för
 * användaren - bara en generisk {@code log.warn} i {@link SecurityConfig}
 * som inte skiljer på lokal utveckling (där en tom nyckel är FÖRVÄNTAD, se
 * den klassens Javadoc) och en glömd produktionsvariabel).
 *
 * <p>Samma mönster som {@link ProdProfileGuard} (WINE-43): en varningslogg
 * vid uppstart om driftmiljön känns igen (samma signal,
 * {@code POSTGRESQL_ADDON_HOST}, som {@code application.yml}s datasource-URL
 * och {@code ProdProfileGuard} redan litar på för att skilja Clever
 * Cloud-drift från lokal utveckling/CI) men remember-me-nyckeln ändå saknas.
 * Bygger inget säkerhetsnät i sig - {@code SecurityConfig} registrerar
 * fortfarande medvetet inte remember-me-stödet utan en riktig nyckel (se den
 * klassens Javadoc för varför en förutsägbar default hade varit osäker,
 * inte bara opraktisk) - bara en tydligare, miljömedveten signal än den
 * generiska varningen som redan fanns.
 */
@Component
class RememberMeKeyGuard {

    private static final Logger log = LoggerFactory.getLogger(RememberMeKeyGuard.class);

    static final String CLEVER_CLOUD_SIGNAL_PROPERTY = "POSTGRESQL_ADDON_HOST";
    static final String REMEMBER_ME_KEY_PROPERTY = "winecellar.remember-me.key";

    private final Environment environment;

    RememberMeKeyGuard(Environment environment) {
        this.environment = environment;
    }

    @EventListener
    void onApplicationReady(ApplicationReadyEvent event) {
        warnIfNeeded();
    }

    /**
     * Paketprivat (inte {@code private}) så testet kan anropa den direkt mot
     * en injicerad {@code Environment}-dubblett, utan att behöva boota en hel
     * applikationskontext bara för att verifiera loggraden - samma mönster
     * som {@link ProdProfileGuard#warnIfNeeded()}.
     */
    void warnIfNeeded() {
        boolean looksLikeCleverCloud = environment.getProperty(CLEVER_CLOUD_SIGNAL_PROPERTY) != null;
        boolean rememberMeKeyMissing = !StringUtils.hasText(environment.getProperty(REMEMBER_ME_KEY_PROPERTY));
        if (looksLikeCleverCloud && rememberMeKeyMissing) {
            log.warn("Appen ser ut att köra på Clever Cloud (miljövariabeln {} är satt), men "
                    + "WINECELLAR_REMEMBER_ME_KEY saknas/är tom - \"håll mig inloggad\" är då helt "
                    + "avstängt i produktion (se ADR 0020 och CLAUDE.md, Kända fällor). Sätt "
                    + "miljövariabeln WINECELLAR_REMEMBER_ME_KEY i Clever Cloud-konsolen.",
                    CLEVER_CLOUD_SIGNAL_PROPERTY);
        }
    }
}
