package com.example.winecellar.domain;

import com.example.winecellar.domain.User.UserId;

import java.time.Instant;

/**
 * Ett engångstoken för verifiering av e-postadress eller återställning av
 * lösenord (WINE-59, se ADR 0026). Bara SHA-256-hashen av tokenet lagras -
 * det råa värdet finns bara i mailet till användaren. Tunt, som resten av
 * domänen: giltighetsregler (24 h / 1 h, engångsbruk) ligger i
 * application.TokenService.
 *
 * `pendingPasswordHash` (bara för EMAIL_VERIFICATION, annars null) är lösenordshashen
 * som aktiveras när just det här tokenet löses in - lösenordet är bundet till
 * tokenet (och därmed till brevlådan), inte till kontoraden. Se ADR 0026.
 */
public record UserToken(Long id, UserId userId, Purpose purpose, String tokenHash, Instant expiresAt,
                        String pendingPasswordHash) {

    public enum Purpose {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }
}
