package com.example.winecellar.infrastructure;

import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.domain.Conversation;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.support.SharedPostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WINE-64: samma fail-closed-krav som för in-memory-adaptrarna, bevisat mot
 * de riktiga JPA-adaptrarna och en riktig Postgres.
 */
@SpringBootTest
class JpaRepositoriesFailClosedIT extends SharedPostgres {

    @Autowired
    private JpaWineRepository wineRepository;

    @Autowired
    private JpaConversationRepository conversationRepository;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void städaAnvändare() {
        for (String username : new String[] {"failClosedAlice", "failClosedBob", "failClosedCarol", "failClosedDave"}) {
            userRepository.findByUsername(username).ifPresent(user -> {
                conversationRepository.deleteAllByOwner(user.id());
                wineRepository.deleteAllByOwner(user.id());
                userRepository.deleteById(user.id());
            });
        }
    }

    private UserId user(String username) {
        registrationService.register(username, "testlösenord123");
        return userRepository.findByUsername(username).orElseThrow().id();
    }

    @Test
    void jpaWineRepositorySkaKastaVidNullOwnerMenBaraVisaEgnaVinerFörRiktigÄgare() {
        UserId alice = user("failClosedAlice");
        UserId bob = user("failClosedBob");
        Wine alices = wineRepository.save(Wine.builder().name("FailClosed Barolo").quantity(1).owner(alice).build());
        wineRepository.save(Wine.builder().name("FailClosed Barolo Bobs").quantity(1).owner(bob).build());
        try {
            assertThatThrownBy(() -> wineRepository.findAllByOwner(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> wineRepository.findByIdAndOwner(alices.id(), null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> wineRepository.searchByOwner("barolo", null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> wineRepository.deleteAllByOwner(null)).isInstanceOf(NullPointerException.class);

            assertThat(wineRepository.findAllByOwner(alice)).extracting(Wine::name).containsExactly("FailClosed Barolo");
            assertThat(wineRepository.searchByOwner("barolo", alice)).extracting(Wine::name).containsExactly("FailClosed Barolo");
            assertThat(wineRepository.findByIdAndOwner(alices.id(), bob)).isEmpty();
        } finally {
            wineRepository.deleteAllByOwner(alice);
            wineRepository.deleteAllByOwner(bob);
        }
    }

    @Test
    void jpaConversationRepositorySkaKastaVidNullOwnerMenBaraVisaEgnaKonversationerFörRiktigÄgare() {
        UserId carol = user("failClosedCarol");
        UserId dave = user("failClosedDave");
        Conversation carols = conversationRepository.save(new Conversation(null, carol, "Carols", Instant.now()));
        conversationRepository.save(new Conversation(null, dave, "Daves", Instant.now()));
        try {
            assertThatThrownBy(() -> conversationRepository.findAllByOwner(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> conversationRepository.findByIdAndOwner(carols.id(), null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> conversationRepository.deleteByIdAndOwner(carols.id(), null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> conversationRepository.deleteAllByOwner(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> conversationRepository.countByOwner(null)).isInstanceOf(NullPointerException.class);

            assertThat(conversationRepository.findAllByOwner(carol)).extracting(Conversation::title).containsExactly("Carols");
            assertThat(conversationRepository.findByIdAndOwner(carols.id(), dave)).isEmpty();
            assertThat(conversationRepository.countByOwner(carol)).isEqualTo(1);
        } finally {
            conversationRepository.deleteAllByOwner(carol);
            conversationRepository.deleteAllByOwner(dave);
        }
    }
}
