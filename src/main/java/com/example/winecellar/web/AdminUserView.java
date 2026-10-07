package com.example.winecellar.web;

import com.example.winecellar.domain.User;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Instant;

/**
 * Rendering-klar rad för adminsidans användarlista (WINE-62). Tidsstämplarna
 * formateras med minutprecision i UTC (inte lokal tid) - sidan märker ut
 * "UTC" i kolumnrubrikerna.
 */
record AdminUserView(long id, String username, boolean admin, String createdAt, String lastLoginAt) {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    static AdminUserView of(User user) {
        return new AdminUserView(user.id().value(), user.username(), user.admin(),
                format(user.createdAt()), format(user.lastLoginAt()));
    }

    static String format(Instant instant) {
        return instant == null ? "-" : FORMAT.format(instant);
    }
}
