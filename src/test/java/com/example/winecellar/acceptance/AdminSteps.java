package com.example.winecellar.acceptance;

import com.example.winecellar.application.AdminService;
import com.example.winecellar.application.ConversationRepository;
import com.example.winecellar.support.TestAccounts;
import com.example.winecellar.application.UserRepository;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.Conversation;
import com.example.winecellar.domain.User;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Och;
import io.cucumber.java.sv.Så;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adminfunktionalitet (WINE-61). Körs mot Spring-hanterade bönor och en
 * riktig Postgres (Testcontainers) - inte InMemory-dubbletterna - eftersom
 * raderingens FK-ordning (meddelanden, konversationer, viner/taggar,
 * användare; inget ON DELETE CASCADE i schemat) bara bevisas av en riktig
 * databas. Städningen mellan scenarier görs av de globala @Before-hooks i
 * PersistenceSteps/RegistrationSteps.
 */
public class AdminSteps {

    private static final String PASSWORD = "ettLösenord123";

    @Autowired
    private AdminService adminService;

    @Autowired
    private TestAccounts testAccounts;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WineService wineService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Det som skapades per användarnamn - behövs för att kunna slå upp rader efter att ägaren raderats. */
    private final Map<String, UserId> userIds = new HashMap<>();
    private final Map<String, List<Long>> wineIds = new HashMap<>();
    private final Map<String, List<Long>> conversationIds = new HashMap<>();

    @Givet("att {string} är admin")
    public void attÄrAdmin(String username) {
        registerUser(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        userRepository.save(new User(user.id(), user.username(), user.hashedPassword(), user.createdAt(),
                user.defaultMinQuantityFilter(), user.ownRatingFromScale(), true, user.lastLoginAt()));
    }

    @Och("att {string} är en vanlig användare")
    public void attÄrEnVanligAnvändare(String username) {
        registerUser(username);
    }

    @Och("att {string} äger vinet {string} med taggen {string} och en konversation med frågan {string}")
    public void attÄgerVinetMedTaggenOchEnKonversationMedFrågan(
            String username, String wineName, String tag, String question) {
        UserId owner = userIds.get(username);
        Wine wine = wineService.save(StepSupport.wineWithName(wineName).toBuilder()
                .owner(owner).tags(Set.of(tag)).build());
        wineIds.computeIfAbsent(username, k -> new ArrayList<>()).add(wine.id().value());
        Conversation conversation = conversationRepository.save(
                new Conversation(null, owner, question, Instant.now()));
        conversationRepository.addMessage(new ChatMessage(
                null, conversation.id(), ChatMessage.Role.USER, question, Instant.now()));
        conversationRepository.addMessage(new ChatMessage(
                null, conversation.id(), ChatMessage.Role.ASSISTANT, "Svar", Instant.now()));
        conversationIds.computeIfAbsent(username, k -> new ArrayList<>()).add(conversation.id().value());
    }

    @När("{string} gör {string} till admin")
    public void görTillAdmin(String actor, String target) {
        assertThat(adminService.makeAdmin(userIds.get(actor), userIds.get(target))).isTrue();
    }

    @När("{string} försöker göra {string} till admin")
    public void försökerGöraTillAdmin(String actor, String target) {
        assertThat(adminService.makeAdmin(userIds.get(actor), userIds.get(target))).isFalse();
    }

    @När("{string} raderar användaren {string}")
    public void raderarAnvändaren(String actor, String target) {
        assertThat(adminService.deleteUser(userIds.get(actor), userIds.get(target))).isTrue();
    }

    @När("{string} försöker radera användaren {string}")
    public void försökerRaderaAnvändaren(String actor, String target) {
        assertThat(adminService.deleteUser(userIds.get(actor), userIds.get(target))).isFalse();
    }

    @Så("ska {string} vara admin")
    public void skaVaraAdmin(String username) {
        assertThat(userRepository.findByUsername(username).orElseThrow().admin()).isTrue();
    }

    @Så("ska {string} inte vara admin")
    public void skaInteVaraAdmin(String username) {
        assertThat(userRepository.findByUsername(username).orElseThrow().admin()).isFalse();
    }

    @Så("ska användaren {string} inte finnas")
    public void skaAnvändarenInteFinnas(String username) {
        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    @Så("ska användaren {string} fortfarande finnas")
    public void skaAnvändarenFortfarandeFinnas(String username) {
        assertThat(userRepository.findByUsername(username)).isPresent();
    }

    @Så("ska inga viner, taggar, konversationer eller meddelanden som tillhörde {string} finnas kvar")
    public void skaIngaDataFinnasKvar(String username) {
        assertThat(wineIds.get(username)).isNotEmpty();
        assertThat(conversationIds.get(username)).isNotEmpty();
        assertThat(count("wines", "owner_id", userIds.get(username).value())).isZero();
        assertThat(count("conversations", "owner_id", userIds.get(username).value())).isZero();
        for (Long wineId : wineIds.get(username)) {
            assertThat(count("wine_tags", "wine_id", wineId)).isZero();
        }
        for (Long conversationId : conversationIds.get(username)) {
            assertThat(count("chat_messages", "conversation_id", conversationId)).isZero();
        }
    }

    @Så("ska {string} fortfarande äga vinet {string} med taggen {string} och en konversation med frågan {string}")
    public void skaFortfarandeÄgaVinet(String username, String wineName, String tag, String question) {
        UserId owner = userIds.get(username);
        assertThat(wineService.listWines(owner))
                .singleElement()
                .satisfies(wine -> {
                    assertThat(wine.name()).isEqualTo(wineName);
                    assertThat(wine.tags()).containsExactly(tag);
                });
        assertThat(conversationRepository.findAllByOwner(owner))
                .singleElement()
                .satisfies(conversation -> {
                    assertThat(conversation.title()).isEqualTo(question);
                    assertThat(conversationRepository.countMessages(conversation.id())).isEqualTo(2);
                });
    }

    @Så("ska {string} se användarna {string} och {string} i adminlistan")
    public void skaSeAnvändarnaIAdminlistan(String actor, String first, String second) {
        assertThat(adminService.listUsers(userIds.get(actor)))
                .extracting(User::username)
                .contains(first, second);
    }

    @Så("ska {string} inte se några användare i adminlistan")
    public void skaInteSeNågraAnvändareIAdminlistan(String actor) {
        assertThat(adminService.listUsers(userIds.get(actor))).isEmpty();
    }

    private void registerUser(String username) {
        testAccounts.register(username, PASSWORD);
        userIds.put(username, userRepository.findByUsername(username).orElseThrow().id());
    }

    private int count(String table, String column, Long id) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + column + " = ?",
                Integer.class, id);
    }
}
