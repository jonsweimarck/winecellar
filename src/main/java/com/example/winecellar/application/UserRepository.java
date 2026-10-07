package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;

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

    /** WINE-61: scopas inte - anropande kod (AdminService) kontrollerar behörigheten. */
    void deleteById(UserId id);
}
