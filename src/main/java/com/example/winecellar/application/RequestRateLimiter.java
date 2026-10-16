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
        Instant cutoff = now.minus(window);
        Deque<Instant> times = requests.get(key);
        if (times == null) {
            if (requests.size() >= maxKeys) {
                requests.values().removeIf(t -> t.isEmpty() || !t.peekLast().isAfter(cutoff));
            }
            if (requests.size() >= maxKeys) {
                return false;
            }
            times = new ArrayDeque<>();
            requests.put(key, times);
        }
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.pollFirst();
        }
        if (times.size() >= maxRequests) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    /**
     * Icke-förbrukande kontroll: skulle {@link #tryAcquire} för nyckeln lyckas just nu?
     * Används för att avgöra om ett mail över huvud taget får skickas INNAN något ändras
     * (kvoten förbrukas först efter en lyckad utfärdning).
     */
    public synchronized boolean hasCapacity(String key) {
        Instant cutoff = clock.instant().minus(window);
        Deque<Instant> times = requests.get(key);
        if (times == null) {
            return requests.size() < maxKeys || requests.values().stream()
                    .anyMatch(t -> t.isEmpty() || !t.peekLast().isAfter(cutoff));
        }
        long live = times.stream().filter(t -> t.isAfter(cutoff)).count();
        return live < maxRequests;
    }

    synchronized int size() {
        return requests.size();
    }
}
