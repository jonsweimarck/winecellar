package com.example.winecellar.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/**
 * Globalt tak för utgående mail (WINE-65, skydd mot massregistrering/mailmissbruk): högst N mail
 * per timme OCH högst M per dygn, för ALLA mailtyper gemensamt (registrering, "ny länk" och
 * glömt lösenord). Kompletterar kvoten per adress, som inte stoppar en angripare som
 * registrerar många olika adresser. Slut tak = anroparen gör ingenting (inget konto, inget
 * mail) och svarar ändå neutralt. Ingen CAPTCHA och ingen IP-baserad begränsning (ADR 0026).
 *
 * I minnet, som {@link RequestRateLimiter} (passar enkelinstansdrift). Timtaket klipps till
 * en bråkdel av nyckeltaket i RequestRateLimiter: eftersom varje utskick bara ger en levande
 * post i kvoten per adress kan kartorna då aldrig fyllas av levande poster, och en full karta
 * kan alltså inte tyst neka nya adresser.
 */
@Service
public class MailBudget {

    private static final Logger log = LoggerFactory.getLogger(MailBudget.class);

    static final String KEY = "global";
    /** Timtaket får aldrig närma sig nyckeltaket i per-adress-kartorna. */
    static final int MAX_HOURLY_CAP = RequestRateLimiter.DEFAULT_MAX_KEYS / 2;

    private final int perHour;
    private final RequestRateLimiter hourly;
    private final RequestRateLimiter daily;

    public MailBudget(@Value("${winecellar.mail.max-per-hour:30}") int perHour,
                      @Value("${winecellar.mail.max-per-day:150}") int perDay, Clock clock) {
        if (perHour < 1 || perDay < 1) {
            throw new IllegalArgumentException(
                    "winecellar.mail.max-per-hour och max-per-day måste vara minst 1 (fick " + perHour + "/" + perDay + ")");
        }
        if (perHour > MAX_HOURLY_CAP) {
            log.warn("winecellar.mail.max-per-hour={} är för högt och begränsas till {}.", perHour, MAX_HOURLY_CAP);
            perHour = MAX_HOURLY_CAP;
        }
        this.perHour = perHour;
        this.hourly = new RequestRateLimiter(perHour, Duration.ofHours(1), clock);
        this.daily = new RequestRateLimiter(perDay, Duration.ofDays(1), clock);
    }

    /** Förbrukar en plats i både timme- och dygnstaket, eller ingen alls. @return false om något tak är nått. */
    public synchronized boolean tryAcquire() {
        if (!hourly.hasCapacity(KEY) || !daily.hasCapacity(KEY)) {
            return false;
        }
        hourly.tryAcquire(KEY);
        daily.tryAcquire(KEY);
        return true;
    }

    /** Lämnar tillbaka en plats som tagits men aldrig använts (t.ex. när kontot inte kunde skapas). */
    public synchronized void release() {
        hourly.release(KEY);
        daily.release(KEY);
    }

    int hourlyCap() {
        return perHour;
    }
}
