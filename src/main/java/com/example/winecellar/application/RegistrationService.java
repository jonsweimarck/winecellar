package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Öppen självregistrering (WINE-11, se ADR 0013) - vem som helst kan
 * skapa ett konto. Unikhetskontrollen på username sitter här (och i
 * databasens UNIQUE-constraint på users.username som sista skyddsnät),
 * inte i domänobjektet User - se User.java.
 */
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public RegistrationResult register(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            return new RegistrationResult.UsernameTaken();
        }
        // Auto-inloggningen direkt efter registrering publicerar ingen
        // inloggningshändelse - senaste login = skapad vid registrering.
        Instant now = Instant.now();
        User user = userRepository.save(
                new User(null, username, passwordEncoder.encode(password), now, 1, false, false, now));
        return new RegistrationResult.Registered(user);
    }
}
