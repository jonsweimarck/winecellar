package com.example.winecellar.infrastructure;

import com.example.winecellar.domain.Conversation;
import com.example.winecellar.domain.Conversation.ConversationId;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.Wine.WineId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WINE-64: ett null-owner betyder aldrig "alla användares data" - varje
 * owner-metod i repositoryna kastar i stället.
 */
class InMemoryRepositoriesFailClosedTest {

    private static final UserId ALICE = new UserId(1L);
    private static final UserId BOB = new UserId(2L);

    @Test
    void wineRepositorySkaKastaVidNullOwnerIVarjeOwnerMetod() {
        InMemoryWineRepository repository = new InMemoryWineRepository();
        Wine saved = repository.save(Wine.builder().name("Barolo").quantity(1).owner(ALICE).build());

        assertThatThrownBy(() -> repository.findAllByOwner(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findByIdAndOwner(saved.id(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.searchByOwner("barolo", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.deleteAllByOwner(null)).isInstanceOf(NullPointerException.class);
        assertThat(repository.findAllByOwner(ALICE)).hasSize(1);
    }

    @Test
    void wineRepositorySkaBaraVisaEnRiktigÄgaresEgnaViner() {
        InMemoryWineRepository repository = new InMemoryWineRepository();
        Wine alices = repository.save(Wine.builder().name("Barolo").quantity(1).owner(ALICE).build());
        repository.save(Wine.builder().name("Barolo Bobs").quantity(1).owner(BOB).build());

        assertThat(repository.findAllByOwner(ALICE)).extracting(Wine::name).containsExactly("Barolo");
        assertThat(repository.searchByOwner("barolo", ALICE)).extracting(Wine::name).containsExactly("Barolo");
        assertThat(repository.findByIdAndOwner(alices.id(), BOB)).isEmpty();
        assertThat(repository.findByIdAndOwner(new WineId(999L), ALICE)).isEmpty();
    }

    @Test
    void conversationRepositorySkaKastaVidNullOwnerIVarjeOwnerMetod() {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();
        Conversation saved = repository.save(new Conversation(null, ALICE, "Fråga", Instant.now()));

        assertThatThrownBy(() -> repository.findAllByOwner(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findByIdAndOwner(saved.id(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.deleteByIdAndOwner(saved.id(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.deleteAllByOwner(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.countByOwner(null)).isInstanceOf(NullPointerException.class);
        assertThat(repository.countByOwner(ALICE)).isEqualTo(1);
    }

    @Test
    void conversationRepositorySkaBaraVisaEnRiktigÄgaresEgnaKonversationer() {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();
        Conversation alices = repository.save(new Conversation(null, ALICE, "Alices", Instant.now()));
        repository.save(new Conversation(null, BOB, "Bobs", Instant.now()));

        assertThat(repository.findAllByOwner(ALICE)).extracting(Conversation::title).containsExactly("Alices");
        assertThat(repository.findByIdAndOwner(alices.id(), BOB)).isEmpty();
        assertThat(repository.findByIdAndOwner(new ConversationId(999L), ALICE)).isEmpty();
    }

    @Test
    void wineRepositorySaveSkaKastaVidNullOwner() {
        InMemoryWineRepository repository = new InMemoryWineRepository();

        assertThatThrownBy(() -> repository.save(Wine.builder().name("Barolo").quantity(1).build()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void conversationRepositorySaveSkaKastaVidNullOwner() {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();

        assertThatThrownBy(() -> repository.save(new Conversation(null, null, "Fråga", Instant.now())))
                .isInstanceOf(NullPointerException.class);
    }
}
