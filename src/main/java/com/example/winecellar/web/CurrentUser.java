package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
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

    /**
     * WINE-41: vinlistans sparade "Antal flaskor fler än"-standardval för
     * den inloggade användaren - GET /:s fallback när requesten saknar en
     * explicit `minQuantity`-queryparameter. `0` (samma orelse-fallback
     * som owner(...) ovan) om användaren av någon anledning inte skulle
     * hittas - i praktiken bara det ofarliga skyddsnätet som redan gäller
     * för owner(...).
     */
    static int defaultMinQuantityFilter(Authentication authentication, UserRepository userRepository) {
        return userRepository.findByUsername(authentication.getName())
                .map(User::defaultMinQuantityFilter)
                .orElse(0);
    }
}
