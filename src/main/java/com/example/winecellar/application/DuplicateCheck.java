package com.example.winecellar.application;

import com.example.winecellar.domain.Wine;

/**
 * Resultatet av WineService.checkForDuplicate(...) - se WINE-6 och
 * Wine.matchesIdentityOf(...)/hasCompleteIdentity() för själva
 * matchningslogiken.
 */
public sealed interface DuplicateCheck {

    record NoDuplicate() implements DuplicateCheck {
    }

    record PartialDuplicate(Wine existing) implements DuplicateCheck {
    }

    record FullDuplicate(Wine existing) implements DuplicateCheck {
    }
}
