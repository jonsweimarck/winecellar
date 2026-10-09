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
 * adress - lika för känd och okänd adress, så att begränsningen inte avslöjar
 * vilka adresser som finns.
 */
public class RequestRateLimiter {

    private static final int PURGE_THRESHOLD = 1000;

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> requests = new HashMap<>();

    public RequestRateLimiter(int maxRequests, Duration window, Clock clock) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
    }

    /** @return true (och räknar upp) om begäran är tillåten, annars false */
    public synchronized boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        if (requests.size() > PURGE_THRESHOLD) {
            requests.values().removeIf(times -> times.isEmpty() || !times.peekLast().isAfter(cutoff));
        }
        Deque<Instant> times = requests.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.pollFirst();
        }
        if (times.size() >= maxRequests) {
            return false;
        }
        times.addLast(now);
        return true;
    }
}
