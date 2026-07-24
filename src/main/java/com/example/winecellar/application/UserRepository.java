package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    /**
     * Används av både registrering (WINE-11, unikhetskontroll) och
     * inloggning (WINE-12, `UserDetailsService`).
     */
    Optional<User> findByUsername(String username);
}
