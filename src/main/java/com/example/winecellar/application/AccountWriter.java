package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.UserToken;
import com.example.winecellar.domain.UserToken.Purpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Atomära kontoändringar som löser in ett token (WINE-59, ADR 0026; WINE-65): aktivering efter
 * verifiering och lösenordsåterställning. Egen böna så att {@code @Transactional} faktiskt
 * gäller (ett självanrop inom en tjänst hade gått förbi proxyn). Lösenordet/verifierad-flaggan
 * skrivs med riktade uppdateringar - aldrig genom att spara hela User - så att en samtidig ändring
 * av t.ex. senaste login eller adminflaggan inte skrivs över.
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
        boolean activated = userRepository.activateAccount(token.userId(), newPasswordHash);
        tokenService.revokeAll(token.userId(), Purpose.EMAIL_VERIFICATION);
        return activated;
    }

    /**
     * Löser in ett återställningstoken: i EN transaktion förbrukas tokenet, lösenordet byts och
     * användarens övriga återställningstokens raderas. Felar något efter att tokenet förbrukats
     * rullas allt tillbaka - länken är då fortfarande giltig och lösenordet oförändrat.
     *
     * @return false om någon annan hann före eller användaren saknas
     */
    @Transactional
    public boolean resetPassword(UserToken token, String newPasswordHash) {
        if (!tokenService.consume(token)) {
            return false;
        }
        if (!userRepository.updateHashedPassword(token.userId(), newPasswordHash)) {
            return false;
        }
        tokenService.revokeAll(token.userId(), Purpose.PASSWORD_RESET);
        return true;
    }
}
