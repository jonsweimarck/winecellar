package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User.UserId;
import org.springframework.security.core.Authentication;

/**
 * WINE-22: extraherad ur `WineController.currentOwner(...)` när
 * `ExportController` fick samma behov - andra verkliga anropsplatsen,
 * inte en förhandsabstraktion.
 *
 * `null` betyder oscopeat, inte "ägs av ingen" - ursprungligen till för
 * de hårdkodade admin/readonly-kontona (som inte fanns i users-tabellen
 * och medvetet var oscopeade under övergången till WINE-15). Sedan
 * WINE-15 (admin/readonly borttagna) hittar `userRepository` alltid en
 * träff för en riktigt inloggad `Authentication` - `.orElse(null)` är
 * kvar som ett ofarligt, numera i praktiken oanvänt skyddsnät.
 */
final class CurrentUser {

    private CurrentUser() {
    }

    static UserId owner(Authentication authentication, UserRepository userRepository) {
        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.id())
                .orElse(null);
    }
}
