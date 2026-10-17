package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    /**
     * Används av både registrering (WINE-11, unikhetskontroll) och
     * inloggning (WINE-12, `UserDetailsService`).
     */
    Optional<User> findByUsername(String username);

    /** WINE-61: adminsidans lista över alla användare. */
    List<User> findAll();

    /**
     * WINE-62: sätter BARA senaste inloggningstidpunkt via en egen riktad
     * skrivning, så att den inte skriver över t.ex. inställningar eller
     * adminflaggan. Ett mycket snävt race finns åt andra hållet:
     * SettingsController/AdminService.makeAdmin läser och sparar hela User och
     * kan i teorin ge ett något äldre värde - nästa inloggning rättar det.
     * No-op om användaren inte finns.
     */
    void updateLastLogin(UserId id, Instant at);

    /**
     * WINE-65: sätter BARA lösenordshashen (riktad skrivning - ändrar aldrig t.ex. senaste login
     * eller adminflaggan, som en hel-User-sparning kunde skriva över vid en samtidig ändring).
     *
     * @return false om användaren inte finns
     */
    boolean updateHashedPassword(UserId id, String hashedPassword);

    /**
     * WINE-65: aktiverar ett konto - sätter lösenordshashen och markerar kontot verifierat - men BARA
     * om det fortfarande är overifierat (villkorad, riktad skrivning). Ett redan verifierat kontos
     * lösenord ändras alltså aldrig härifrån.
     *
     * @return false om användaren saknas eller redan är verifierad (inget ändrades)
     */
    boolean activateAccount(UserId id, String hashedPassword);

    /** WINE-65: overifierade konton skapade före tidpunkten (kandidater för städning). */
    List<User> findUnverifiedCreatedBefore(Instant createdBefore);

    /** WINE-61: scopas inte - anropande kod (AdminService) kontrollerar behörigheten. */
    void deleteById(UserId id);
}
