package com.example.winecellar.domain;

import java.time.Instant;

/**
 * Tunt, som {@link Wine} (se ADR 0001) - inga regler att skydda här.
 * Unikhetskontroll för `username` hör hemma i registreringsflödet
 * (WINE-11), inte i domänobjektet. `hashedPassword` är alltid redan
 * hashat när det når det här objektet - hashningen sker i
 * infrastruktur-/webblagret via den befintliga `PasswordEncoder`-beanen
 * (se `SecurityConfig`), inte här.
 *
 * `defaultMinQuantityFilter` (WINE-41) är vinlistans förvalda
 * "Antal flaskor fler än"-filter för den här användaren - vad `GET /`
 * faller tillbaka till när requesten inte har en explicit
 * `minQuantity`-queryparameter (se WineController). Default 0 för nya
 * konton (se RegistrationService) - "fler än 0", dvs. utdruckna viner
 * (antal 0) döljs som standard.
 */
public record User(
        UserId id,
        String username,
        String hashedPassword,
        Instant createdAt,
        int defaultMinQuantityFilter
) {

    public record UserId(Long value) {
    }
}
