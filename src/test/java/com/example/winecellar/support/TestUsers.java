package com.example.winecellar.support;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;

import java.time.Instant;

/**
 * Testfabrik (WINE-59): ett redan VERIFIERAT konto. Ligger medvetet i testkoden
 * och inte som konstruktor på User - en publik "verifierad som standard"-
 * konstruktor i produktionskoden hade låtit en missad kopia tyst verifiera ett konto.
 */
public final class TestUsers {

    private TestUsers() {
    }

    public static User verifiedUser(UserId id, String username, String hashedPassword, Instant createdAt,
                                    int defaultMinQuantityFilter, boolean ownRatingFromScale, boolean admin,
                                    Instant lastLoginAt) {
        return new User(id, username, hashedPassword, createdAt, defaultMinQuantityFilter, ownRatingFromScale,
                admin, lastLoginAt, true);
    }
}
