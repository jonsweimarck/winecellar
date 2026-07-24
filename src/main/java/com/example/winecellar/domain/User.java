package com.example.winecellar.domain;

import java.time.Instant;

/**
 * Tunt, som {@link Wine} (se ADR 0001) - inga regler att skydda här.
 * Unikhetskontroll för `username` hör hemma i registreringsflödet
 * (WINE-11), inte i domänobjektet. `hashedPassword` är alltid redan
 * hashat när det når det här objektet - hashningen sker i
 * infrastruktur-/webblagret via den befintliga `PasswordEncoder`-beanen
 * (se `SecurityConfig`), inte här.
 */
public record User(
        UserId id,
        String username,
        String hashedPassword,
        Instant createdAt
) {

    public record UserId(Long value) {
    }
}
