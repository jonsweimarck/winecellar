package com.example.winecellar.web;

import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;

/**
 * Slår upp den inloggade användarens post. FAIL-CLOSED (WINE-61): finns
 * användaren inte (längre) i databasen - t.ex. ett konto som raderats av en
 * admin medan hens session lever kvar - kastas ett autentiseringsfel, som
 * Spring Security översätter till en omdirigering till /login. Det får
 * ALDRIG falla tillbaka på `null`: ett `null`-ägarargument betyder
 * "oscopeat" i repository-/servicelagret (se WineRepository) och hade gett
 * åtkomst till ALLA användares viner.
 */
final class CurrentUser {

    private CurrentUser() {
    }

    static UserId owner(Authentication authentication, UserRepository userRepository) {
        return find(authentication, userRepository).id();
    }

    /**
     * Hämtar den inloggade användarens hela {@link User}-post EN gång - en
     * anropsplats som behöver både ägar-id:t och t.ex. det sparade
     * standardfiltret slipper två separata uppslagningar per request.
     */
    static User find(Authentication authentication, UserRepository userRepository) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Användaren finns inte längre: " + authentication.getName()));
    }

    /** Vinlistans sparade "Antal flaskor minst"-standardval för den inloggade användaren. */
    static int defaultMinQuantityFilter(Authentication authentication, UserRepository userRepository) {
        return find(authentication, userRepository).defaultMinQuantityFilter();
    }

    /** Styr om vinformuläret visar "Eget betyg" som dropdown eller fritext (WINE-50). */
    static boolean ownRatingFromScale(Authentication authentication, UserRepository userRepository) {
        return find(authentication, userRepository).ownRatingFromScale();
    }
}
