package com.example.winecellar.domain;

import com.example.winecellar.domain.User.UserId;

import java.time.Instant;

/**
 * Ett engångstoken för verifiering av e-postadress eller återställning av
 * lösenord (WINE-59, se ADR 0026). Bara SHA-256-hashen av tokenet lagras -
 * det råa värdet finns bara i mailet till användaren. Tunt, som resten av
 * domänen: giltighetsregler (24 h / 1 h, engångsbruk) ligger i
 * application.TokenService.
 */
public record UserToken(Long id, UserId userId, Purpose purpose, String tokenHash, Instant expiresAt) {

    public enum Purpose {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }
}
