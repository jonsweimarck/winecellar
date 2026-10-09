package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.UserToken.Purpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomära skrivningar för registreringsflödet (WINE-59). Egen böna så att
 * {@code @Transactional} faktiskt gäller (ett självanrop inom RegistrationService
 * hade gått förbi proxyn), och så att mailet skickas EFTER commit - anroparen
 * skickar först när metoden returnerat.
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
     * Skriver över lösenordshashen för ett overifierat konto OCH ogiltigförklarar
     * alla dess verifieringslänkar i en transaktion - antingen båda eller ingen.
     */
    @Transactional
    public User overwritePasswordAndRevokeLinks(User user, String newHash) {
        User saved = userRepository.save(user.withHashedPassword(newHash));
        tokenService.revokeAll(saved.id(), Purpose.EMAIL_VERIFICATION);
        return saved;
    }
}
