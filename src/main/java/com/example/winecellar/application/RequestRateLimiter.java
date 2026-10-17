package com.example.winecellar.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Enkel glidande-fönster-begränsare i minnet (WINE-59). Passar
 * enkelinstansdrift (se ADR 0026); en omstart nollställer räknarna. Används
 * för mailutskick utlösta av anonyma begäranden, nycklade på (normaliserad)
 * adress. Anropas BARA när ett mail faktiskt ska skickas (efter kontouppslag),
 * så okända adresser aldrig hamnar i kartan.
 *
 * Kartan är storleksbegränsad: utgångna poster rensas, och är den ändå full
 * nekas nya nycklar tyst (anroparen svarar ändå neutralt, så ingenting avslöjas).
 * Det ska i praktiken aldrig hända för mailkvoterna (WINE-65): {@link MailBudget}
 * begränsar det totala antalet mail per timme - och därmed antalet adresser med en
 * levande post - till klart under nyckeltaket.
 * Ingen IP-baserad begränsning - X-Forwarded-For är förfalskningsbar (ADR 0020).
 */
public class RequestRateLimiter {

    static final int DEFAULT_MAX_KEYS = 10_000;

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final int maxKeys;
    private final Map<String, Deque<Instant>> requests = new HashMap<>();

    public RequestRateLimiter(int maxRequests, Duration window, Clock clock) {
        this(maxRequests, window, clock, DEFAULT_MAX_KEYS);
    }

    RequestRateLimiter(int maxRequests, Duration window, Clock clock, int maxKeys) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
        this.maxKeys = maxKeys;
    }

    /** @return true (och räknar upp) om begäran är tillåten, annars false */
    public synchronized boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Deque<Instant> times = livePrunedEntry(key, now);
        if (times == null) {
            if (!roomForNewKey(now)) {
                return false;
            }
            times = new ArrayDeque<>();
            requests.put(key, times);
        }
        if (times.size() >= maxRequests) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    /**
     * Icke-förbrukande kontroll: skulle {@link #tryAcquire} för nyckeln lyckas just nu?
     * Delar fönster- och rensningslogik med tryAcquire (samma privata metoder), så de två
     * kan aldrig ge olika svar. Att kontrollera och sedan anropa tryAcquire är INTE atomärt:
     * använd tryAcquire direkt när ett slut på kvoten ska avgöra om något får göras.
     */
    public synchronized boolean hasCapacity(String key) {
        Instant now = clock.instant();
        Deque<Instant> times = livePrunedEntry(key, now);
        if (times == null) {
            return roomForNewKey(now);
        }
        return times.size() < maxRequests;
    }

    /**
     * Återbetalar den senast förbrukade platsen för nyckeln (t.ex. när ett mail som kvoten
     * räknade för aldrig kunde skickas). No-op om nyckeln saknas eller inga platser finns kvar.
     */
    public synchronized void release(String key) {
        Deque<Instant> times = requests.get(key);
        if (times != null) {
            times.pollLast();
        }
    }

    /** Nyckelns kö efter att utgångna tidsstämplar tagits bort, eller null om nyckeln saknas. */
    private Deque<Instant> livePrunedEntry(String key, Instant now) {
        Deque<Instant> times = requests.get(key);
        if (times != null) {
            Instant cutoff = now.minus(window);
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
                times.pollFirst();
            }
        }
        return times;
    }

    /** Finns plats för en ny nyckel? Är kartan full rensas först utgångna poster. */
    private boolean roomForNewKey(Instant now) {
        if (requests.size() >= maxKeys) {
            Instant cutoff = now.minus(window);
            requests.values().removeIf(t -> t.isEmpty() || !t.peekLast().isAfter(cutoff));
        }
        return requests.size() < maxKeys;
    }

    synchronized int size() {
        return requests.size();
    }
}
