package com.example.winecellar.application;

import com.example.winecellar.domain.User;

/**
 * Resultatet av RegistrationService.register(...) - se WINE-11.
 */
public sealed interface RegistrationResult {

    record Registered(User user) implements RegistrationResult {
    }

    record UsernameTaken() implements RegistrationResult {
    }
}
