package com.example.winecellar.application;

import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adminfunktionalitet (WINE-61, se ADR 0025). Varje metod tar den AGERANDE
 * användaren och kontrollerar själv att hen är admin - behörigheten hänger
 * alltså inte bara på att webblagret (SecurityConfig) döljer/spärrar
 * adminsidan. En icke-admin får tom lista / ett no-op, inte ett fel (samma
 * "bete sig som om det inte fanns"-princip som resten av appen).
 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final WineRepository wineRepository;
    private final ConversationRepository conversationRepository;

    public AdminService(UserRepository userRepository, WineRepository wineRepository,
                        ConversationRepository conversationRepository) {
        this.userRepository = userRepository;
        this.wineRepository = wineRepository;
        this.conversationRepository = conversationRepository;
    }

    /** Alla användare, alfabetiskt - tom lista om {@code actor} inte är admin. */
    public List<User> listUsers(UserId actor) {
        if (!isAdmin(actor)) {
            return List.of();
        }
        return userRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(User::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** @return true om {@code target} faktiskt gjordes (eller redan var) admin av en admin. */
    public boolean makeAdmin(UserId actor, UserId target) {
        if (!isAdmin(actor)) {
            return false;
        }
        return userRepository.findById(target).map(user -> {
            if (!user.admin()) {
                userRepository.save(new User(user.id(), user.username(), user.hashedPassword(),
                        user.createdAt(), user.defaultMinQuantityFilter(), user.ownRatingFromScale(), true));
            }
            return true;
        }).orElse(false);
    }

    /**
     * Raderar användaren och allt hen äger. Ingen {@code ON DELETE CASCADE} i
     * schemat, så allt raderas explicit i en transaktion i FK-ordning:
     * chattmeddelanden och konversationer, viner (med taggar), sist
     * användaren. Spärrar en admin från att radera sig själv (och därmed
     * sista admin) - no-op, inte ett fel.
     *
     * @return true om användaren raderades
     */
    @Transactional
    public boolean deleteUser(UserId actor, UserId target) {
        if (!isAdmin(actor) || actor.equals(target) || userRepository.findById(target).isEmpty()) {
            return false;
        }
        conversationRepository.deleteAllByOwner(target);
        wineRepository.deleteAllByOwner(target);
        userRepository.deleteById(target);
        return true;
    }

    private boolean isAdmin(UserId actor) {
        return actor != null && userRepository.findById(actor).map(User::admin).orElse(false);
    }
}
