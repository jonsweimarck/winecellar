package com.example.winecellar.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Uppstartsvarning (WINE-59, ADR 0026) i samma anda som {@link RememberMeKeyGuard}
 * och {@link ProdProfileGuard}: om Clever Cloud-drift känns igen
 * ({@code POSTGRESQL_ADDON_HOST}) men {@code WINECELLAR_MAIL_HOST} eller
 * {@code WINECELLAR_BASE_URL} saknas, loggas en tydlig varning. Utan SMTP skickas
 * inga verifierings-/återställningsmail (inga nya konton kan aktiveras), och utan
 * bas-URL pekar länkarna i mailen på localhost. Ett säkerhetsnät, ingen fix i sig.
 */
@Component
class MailConfigGuard {

    private static final Logger log = LoggerFactory.getLogger(MailConfigGuard.class);

    static final String MAIL_HOST_VARIABLE = "WINECELLAR_MAIL_HOST";
    static final String BASE_URL_VARIABLE = "WINECELLAR_BASE_URL";

    private final Environment environment;

    MailConfigGuard(Environment environment) {
        this.environment = environment;
    }

    @EventListener
    void onApplicationReady(ApplicationReadyEvent event) {
        warnIfNeeded();
    }

    /** Paketprivat så testet kan anropa den direkt, som {@link RememberMeKeyGuard#warnIfNeeded()}. */
    void warnIfNeeded() {
        if (environment.getProperty(RememberMeKeyGuard.CLEVER_CLOUD_SIGNAL_PROPERTY) == null) {
            return;
        }
        if (!StringUtils.hasText(environment.getProperty(MAIL_HOST_VARIABLE))) {
            log.warn("Appen ser ut att köra på Clever Cloud, men {} saknas/är tom - inga verifierings- eller "
                    + "återställningsmail skickas, så inga nya konton kan aktiveras (se ADR 0026 och CLAUDE.md). "
                    + "Sätt WINECELLAR_MAIL_* i Clever Cloud-konsolen.", MAIL_HOST_VARIABLE);
        }
        if (!StringUtils.hasText(environment.getProperty(BASE_URL_VARIABLE))) {
            log.warn("Appen ser ut att köra på Clever Cloud, men {} saknas/är tom - länkarna i mailen pekar då "
                    + "på localhost (se ADR 0026 och CLAUDE.md). Sätt appens publika HTTPS-adress.",
                    BASE_URL_VARIABLE);
        }
    }
}
