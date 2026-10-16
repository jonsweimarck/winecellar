package com.example.winecellar.application;

import com.example.winecellar.domain.User;

/**
 * Resultatet av RegistrationService.register(...) - se WINE-11 och WINE-59.
 */
public sealed interface RegistrationResult {

    record Registered(User user) implements RegistrationResult {
    }

    record UsernameTaken() implements RegistrationResult {
    }

    /** Användarnamnet ser inte ut som en e-postadress (WINE-59). */
    record InvalidEmail() implements RegistrationResult {
    }
}
