package com.example.winecellar.acceptance;

import com.example.winecellar.application.RegistrationService;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.Conversation;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.infrastructure.JpaConversationRepository;
import com.example.winecellar.infrastructure.JpaWineRepository;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Till skillnad från de övriga stegklasserna körs dessa mot Spring-hanterade
 * bönor (WineService/JpaWineRepository) och en riktig Postgres via
 * Testcontainers - se CucumberSpringConfiguration. Har utökats till att
 * även täcka sökning (se sokning-mot-postgres.feature) - samma skäl som
 * omstartsscenariot: JpaWineRepositorys native query kan bete sig
 * annorlunda än InMemoryWineRepository (WINE-10 visade det, se CLAUDE.md).
 */
public class PersistenceSteps {

    private static final String TESTKONTO_ANVÄNDARNAMN = "persistenceStepsTest";
    private static final String TESTKONTO_LÖSENORD = "testlösenord123";

    @Autowired
    private WineService wineService;

    @Autowired
    private JpaWineRepository wineRepository;

    @Autowired
    private JpaConversationRepository conversationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    private List<Wine> sökresultat;
    private UserId ägare;
    private Conversation.ConversationId latestConversationId;

    /**
     * WINE-15: `owner_id` är `NOT NULL` i databasen och `wines.owner_id`
     * är en FK mot `users` - till skillnad från InMemoryWineRepository
     * (som andra stegklasser använder) tvingar en riktig Postgres nu
     * fram BÅDE att varje vin har en ägare OCH att viner tas bort innan
     * deras ägare får raderas.
     *
     * Delad, global städning (Cucumber-JVM kör `@Before`-hooks från ALLA
     * laddade stegklasser för VARJE scenario, inte bara klasser vars steg
     * faktiskt förekommer i scenariot - samma delade-tillstånd-fälla som
     * CLAUDE.md varnar för, fast mellan klasser istället för inom en)
     * kräver en tredelad ordning över två klasser:
     * 1. Radera ALLA viner (`order = -1`, den här metoden) - måste ske
     *    FÖRST, annars slår `RegistrationSteps.reset()`s
     *    `userRepository.deleteAll()` i en FK-överträdelse mot kvarvarande
     *    viner från föregående scenario (upptäckt av `mvn verify`: "update
     *    or delete on table users violates foreign key constraint ...
     *    still referenced from table wines").
     * 2. Radera ALLA users (`RegistrationSteps.reset()`, `order = 0`) -
     *    säkert nu när steg 1 redan tömt `wines`.
     * 3. Registrera/slå upp testkontot (`order = 1`,
     *    {@link #registreraTestkonto()}) - måste ske EFTER steg 2, annars
     *    raderar steg 2 kontot precis efter att `ägare` pekat ut det
     *    (den ursprungliga buggen den här ordningen löste, se git-historik).
     */
    @Before(order = -1)
    public void raderaAllaViner() {
        wineRepository.deleteAll();
    }

    /**
     * WINE-48: samma FK-fälla mot `users` som `raderaAllaViner` löser för
     * `wines` - `conversations.owner_id` är också `NOT NULL`. Ingen inbördes
     * ordning krävs mellan de två raderingarna (wines/conversations
     * refererar inte varandra, bara users var för sig), bara att båda sker
     * innan `RegistrationSteps.reset()` (order 0).
     */
    @Before(order = -1)
    public void raderaAllaKonversationer() {
        conversationRepository.deleteAll();
    }

    @Before(order = 1)
    public void registreraTestkonto() {
        registrationService.register(TESTKONTO_ANVÄNDARNAMN, TESTKONTO_LÖSENORD);
        ägare = userRepository.findByUsername(TESTKONTO_ANVÄNDARNAMN).orElseThrow().id();
    }

    @Givet("att vinet {string} är sparat i källaren")
    public void attVinetÄrSparatIKällaren(String name) {
        wineService.save(StepSupport.wineWithName(name).toBuilder().owner(ägare).build());
    }

    @Givet("att vinet {string} med druvan {string} är sparat i källaren")
    public void attVinetMedDruvanÄrSparatIKällaren(String name, String druva) {
        wineService.save(StepSupport.wineWithName(name).toBuilder().grapes(druva).owner(ägare).build());
    }

    @När("applikationen startas om")
    public void applikationenStartasOm() {
        entityManager.clear();
    }

    @När("jag söker efter {string} mot databasen")
    public void jagSökerEfterMotDatabasen(String sökord) {
        sökresultat = wineRepository.searchByOwner(sökord, null);
    }

    @Så("ska vinet {string} fortfarande finnas i källaren")
    public void skaVinetFortfarandeFinnasIKällaren(String name) {
        assertThat(wineService.listWines(null)).anySatisfy(wine -> assertThat(wine.name()).isEqualTo(name));
    }

    @Så("ska vinet {string} finnas i sökresultatet")
    public void skaVinetFinnasISökresultatet(String name) {
        assertThat(sökresultat).anySatisfy(wine -> assertThat(wine.name()).isEqualTo(name));
    }

    @Givet("att jag har sparat en konversation med frågan {string} och svaret {string}")
    public void attJagHarSparatEnKonversationMedFråganOchSvaret(String question, String answer) {
        Conversation conversation = conversationRepository.save(new Conversation(null, ägare, question, Instant.now()));
        conversationRepository.addMessage(new ChatMessage(null, conversation.id(), ChatMessage.Role.USER, question, Instant.now()));
        conversationRepository.addMessage(new ChatMessage(null, conversation.id(), ChatMessage.Role.ASSISTANT, answer, Instant.now()));
        latestConversationId = conversation.id();
    }

    @Så("ska konversationen fortfarande innehålla frågan {string} och svaret {string}")
    public void skaKonversationenFortfarandeInnehållaFråganOchSvaret(String question, String answer) {
        assertThat(conversationRepository.findMessages(latestConversationId))
                .extracting(ChatMessage::content)
                .contains(question, answer);
    }
}
