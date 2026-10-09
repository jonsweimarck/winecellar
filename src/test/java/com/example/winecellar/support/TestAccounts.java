package com.example.winecellar.support;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Testhjälpare (WINE-59): skapar ett redan VERIFIERAT konto direkt via
 * UserRepository, utan att gå via RegistrationService. Sedan WINE-59 kräver
 * registreringen en e-postadress och ett mailklick innan inloggning är möjlig;
 * de flesta tester (UI, persistens, admin ...) vill bara ha ett konto att
 * logga in med och ska varken behöva e-postformat eller verifieringssteg.
 * Själva registrerings-/verifieringsflödet har egna tester.
 */
@Component
public class TestAccounts {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestAccounts(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        userRepository.save(new User(null, username, passwordEncoder.encode(password), now, 1, false, false, now, true));
    }
}
