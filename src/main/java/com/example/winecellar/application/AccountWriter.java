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
     * vinnaren av ett race går vidare), kontot markeras verifierat och får just det tokenets
     * väntande lösenordshash, och kontots övriga verifieringstokens raderas.
     *
     * @return false om någon annan hann före eller kontot saknas (inget ändras då)
     */
    @Transactional
    public boolean activate(UserToken token) {
        Optional<User> user = userRepository.findById(token.userId());
        if (user.isEmpty() || !tokenService.consume(token)) {
            return false;
        }
        User activated = user.get().withEmailVerified(true);
        if (token.pendingPasswordHash() != null) {
            activated = activated.withHashedPassword(token.pendingPasswordHash());
        }
        userRepository.save(activated);
        tokenService.revokeAll(token.userId(), Purpose.EMAIL_VERIFICATION);
        return true;
    }
}
