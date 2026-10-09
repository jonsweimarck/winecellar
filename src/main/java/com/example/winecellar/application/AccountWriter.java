package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Atomär aktivering av ett konto (WINE-59, ADR 0026). Egen böna så att
 * {@code @Transactional} faktiskt gäller (ett självanrop inom RegistrationService
 * hade gått förbi proxyn).
 */
@Service
public class AccountWriter {

    private final UserRepository userRepository;
    private final TokenService tokenService;

    public AccountWriter(UserRepository userRepository, TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    /**
     * Löser in ett verifieringstoken: i EN transaktion raderas tokenet villkorat (den enda
     * vinnaren av ett race går vidare), och kontot får det valda lösenordet och markeras
     * verifierat. Tillämpas BARA om kontot fortfarande är overifierat - ett redan verifierat
     * kontos lösenord ändras aldrig härifrån.
     *
     * @return false om kontot saknas/redan är verifierat eller någon annan hann före (inget ändras då)
     */
    @Transactional
    public boolean activate(UserToken token, String newPasswordHash) {
        Optional<User> user = userRepository.findById(token.userId());
        if (user.isEmpty()) {
            return false;
        }
        if (user.get().emailVerified()) {
            tokenService.revokeAll(token.userId(), Purpose.EMAIL_VERIFICATION);
            return false;
        }
        if (!tokenService.consume(token)) {
            return false;
        }
        userRepository.save(user.get().withHashedPassword(newPasswordHash).withEmailVerified(true));
        tokenService.revokeAll(token.userId(), Purpose.EMAIL_VERIFICATION);
        return true;
    }
}
