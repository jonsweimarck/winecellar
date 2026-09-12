package com.example.winecellar.acceptance;

import com.example.winecellar.application.ChatResult;
import com.example.winecellar.application.ChatService;
import com.example.winecellar.application.WineService;
import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.ChatMessage.Role;
import com.example.winecellar.domain.Conversation;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import com.example.winecellar.domain.WineType;
import com.example.winecellar.infrastructure.InMemoryConversationRepository;
import com.example.winecellar.infrastructure.InMemoryWineRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.sv.Givet;
import io.cucumber.java.sv.När;
import io.cucumber.java.sv.Så;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Egen stegklass (WINE-48), inte återanvändning av SearchAndFilterSteps
 * "att källaren innehåller följande viner:" - den delar sin WineService-
 * instans med sortering/filtrering, inte med chatten, se CLAUDE.md:s
 * "Kända fällor" om varför två klasser som behöver dela instans måste vara
 * en och samma klass. Gränsvärdena (MAX_*) är medvetet små testkonstanter,
 * inte de riktiga produktionsdefaulten (application.yml) - scenarierna ska
 * kunna nå gränsen utan att skapa tiotals konversationer.
 */
public class ChatSteps {

    private static final int MAX_CONVERSATIONS = 2;
    // 4, inte 2: "fortsätt en befintlig konversation"-scenariot startar redan
    // från en konversation med ett frågasvar-par (2 meddelanden) och lägger
    // sedan till ett till - en gräns på 2 hade blockerat det scenariot också,
    // inte bara "gräns nådd"-scenariot som faktiskt ska testa blockeringen.
    private static final int MAX_MESSAGES_PER_CONVERSATION = 4;
    private static final UserId OWNER = new UserId(1L);

    private static final Map<String, WineType> WINE_TYPE_BY_SWEDISH_LABEL = Map.of(
            "Rött", WineType.RED,
            "Vitt", WineType.WHITE,
            "Rosé", WineType.ROSE,
            "Mousserande", WineType.SPARKLING,
            "Starkvin", WineType.FORTIFIED
    );

    private WineService wineService;
    private FakeWineChatAssistant chatAssistant;
    private InMemoryConversationRepository conversationRepository;
    private ChatService chatService;

    private Conversation currentConversation;
    private ChatResult lastResult;

    @Before
    public void setUp() {
        InMemoryWineRepository wineRepository = new InMemoryWineRepository();
        wineService = new WineService(wineRepository);
        chatAssistant = new FakeWineChatAssistant();
        conversationRepository = new InMemoryConversationRepository();
        chatService = new ChatService(
                conversationRepository, wineRepository, chatAssistant, MAX_CONVERSATIONS, MAX_MESSAGES_PER_CONVERSATION);
    }

    @Givet("att jag har följande viner i källaren:")
    public void attJagHarFöljandeVinerIKällaren(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            wineService.save(Wine.builder()
                    .owner(OWNER)
                    .name(row.get("namn"))
                    .wineType(WINE_TYPE_BY_SWEDISH_LABEL.get(row.get("vintyp")))
                    .country(row.get("land"))
                    .quantity(1)
                    .build());
        }
    }

    @Givet("att chattassistenten svarar {string}")
    public void attChattassistentenSvarar(String reply) {
        chatAssistant.willReply(reply);
    }

    @Givet("att jag har en konversation med frågan {string} och svaret {string}")
    public void attJagHarEnKonversationMedFråganOchSvaret(String question, String answer) {
        currentConversation = singleConversationWithQuestionAndAnswer(question, answer);
    }

    @Givet("att jag redan har det maximala antalet konversationer")
    public void attJagRedanHarDetMaximalaAntaletKonversationer() {
        for (int i = 0; i < MAX_CONVERSATIONS; i++) {
            singleConversationWithQuestionAndAnswer("Fråga " + i, "Svar " + i);
        }
    }

    @Givet("att jag har en konversation med det maximala antalet meddelanden")
    public void attJagHarEnKonversationMedDetMaximalaAntaletMeddelanden() {
        currentConversation = conversationRepository.save(new Conversation(null, OWNER, "Full konversation", Instant.now()));
        for (int i = 0; i < MAX_MESSAGES_PER_CONVERSATION; i++) {
            Role role = i % 2 == 0 ? Role.USER : Role.ASSISTANT;
            conversationRepository.addMessage(new ChatMessage(null, currentConversation.id(), role, "Meddelande " + i, Instant.now()));
        }
    }

    @När("jag startar en ny konversation och frågar {string}")
    public void jagStartarEnNyKonversationOchFrågar(String question) {
        lastResult = chatService.startConversation(OWNER, question);
        if (lastResult instanceof ChatResult.Success success) {
            currentConversation = success.conversation();
        }
    }

    @När("jag frågar {string} i samma konversation")
    public void jagFrågarISammaKonversation(String question) {
        lastResult = chatService.postMessage(OWNER, currentConversation, question);
    }

    @När("jag försöker starta en ny konversation")
    public void jagFörsökerStartaEnNyKonversation() {
        lastResult = chatService.startConversation(OWNER, "Ännu en fråga");
    }

    @När("jag försöker skicka ytterligare ett meddelande i den konversationen")
    public void jagFörsökerSkickaYtterligareEttMeddelandeIDenKonversationen() {
        lastResult = chatService.postMessage(OWNER, currentConversation, "Ännu ett meddelande");
    }

    @När("jag raderar konversationen")
    public void jagRaderarKonversationen() {
        chatService.deleteConversation(OWNER, currentConversation.id());
    }

    @Så("ska konversationen innehålla frågan {string}")
    public void skaKonversationenInnehållaFrågan(String question) {
        assertThat(currentMessages()).extracting(ChatMessage::content).contains(question);
    }

    @Så("ska konversationen innehålla svaret {string}")
    public void skaKonversationenInnehållaSvaret(String answer) {
        assertThat(currentMessages()).extracting(ChatMessage::content).contains(answer);
    }

    @Så("ska jag få ett felmeddelande om att gränsen är nådd")
    public void skaJagFåEttFelmeddelandeOmAttGränsenÄrNådd() {
        assertThat(lastResult).isInstanceOf(ChatResult.LimitReached.class);
    }

    @Så("inga nya konversationer ska ha skapats")
    public void ingaNyaKonversationerSkaHaSkapats() {
        assertThat(conversationRepository.countByOwner(OWNER)).isEqualTo(MAX_CONVERSATIONS);
    }

    @Så("inga nya meddelanden ska ha lagts till i konversationen")
    public void ingaNyaMeddelandenSkaHaLagtsTillIKonversationen() {
        assertThat(conversationRepository.countMessages(currentConversation.id())).isEqualTo(MAX_MESSAGES_PER_CONVERSATION);
    }

    @Så("ska konversationen inte längre finnas")
    public void skaKonversationenInteLängreFinnas() {
        assertThat(conversationRepository.findByIdAndOwner(currentConversation.id(), OWNER)).isEmpty();
    }

    private Conversation singleConversationWithQuestionAndAnswer(String question, String answer) {
        Conversation conversation = conversationRepository.save(new Conversation(null, OWNER, question, Instant.now()));
        conversationRepository.addMessage(new ChatMessage(null, conversation.id(), Role.USER, question, Instant.now()));
        conversationRepository.addMessage(new ChatMessage(null, conversation.id(), Role.ASSISTANT, answer, Instant.now()));
        return conversation;
    }

    private List<ChatMessage> currentMessages() {
        return conversationRepository.findMessages(currentConversation.id());
    }
}
