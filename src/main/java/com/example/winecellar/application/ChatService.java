package com.example.winecellar.application;

import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.ChatMessage.Role;
import com.example.winecellar.domain.Conversation;
import com.example.winecellar.domain.Conversation.ConversationId;
import com.example.winecellar.domain.User.UserId;
import com.example.winecellar.domain.Wine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Orkestrerar chatten om vinsamlingen (WINE-48) - samma princip som
 * ADR 0006 (orkestrering hör hemma i applikationslagret, inte
 * controllern), tillämpad på en tredje gräns (efter sök/filtrering och
 * etikettolkning). Se docs/adr/0021-wine-chat-conversational-llm-integration.md
 * för de bakomliggande arkitekturbesluten.
 *
 * Gränserna (max antal konversationer per användare, max antal meddelanden
 * per konversation) kontrolleras här, INNAN något meddelande sparas eller
 * något anrop görs mot assistenten - en nådd gräns kostar alltså aldrig ett
 * onödigt externt anrop.
 */
@Service
public class ChatService {

    /**
     * Visas som ett felmeddelande (inte sparat som ett {@link ChatMessage})
     * om {@link WineChatAssistant} misslyckas helt - se
     * {@link ChatResult.AssistantUnavailable} för varför inget falskt
     * assistentmeddelande sätts in i konversationen. Public så webblagret
     * kan återanvända exakt samma text.
     */
    public static final String ASSISTANT_UNAVAILABLE_MESSAGE = "Kunde inte få ett svar just nu - försök gärna igen.";

    private final ConversationRepository conversationRepository;
    private final WineRepository wineRepository;
    private final WineChatAssistant assistant;
    private final int maxConversationsPerUser;
    private final int maxMessagesPerConversation;

    /**
     * Serialiserar gränskontrollen ({@code countByOwner}/{@code countMessages}
     * följt av den reserverande skrivningen) mot ett gemensamt lås - utan
     * det kan två nästan samtidiga requests (dubbelklick, två flikar) båda
     * läsa ett värde under gränsen innan någon hunnit skriva, och gränsen
     * överskrids. Ett enda process-internt lås räcker för appens nuvarande
     * driftsform (en instans) - det omfattar bara läs-och-skriv-paret, inte
     * det efterföljande (blockerande) anropet mot assistenten, så en
     * långsam extern förfrågan inte serialiserar ALLA användares chattar.
     */
    private final Object limitGuard = new Object();

    public ChatService(
            ConversationRepository conversationRepository,
            WineRepository wineRepository,
            WineChatAssistant assistant,
            @Value("${winecellar.chat.max-conversations}") int maxConversationsPerUser,
            @Value("${winecellar.chat.max-messages-per-conversation}") int maxMessagesPerConversation) {
        this.conversationRepository = conversationRepository;
        this.wineRepository = wineRepository;
        this.assistant = assistant;
        this.maxConversationsPerUser = maxConversationsPerUser;
        this.maxMessagesPerConversation = maxMessagesPerConversation;
    }

    /**
     * Skapar alltid en ny konversation TILLSAMMANS MED dess första
     * meddelande - det finns ingen tom konversation i den här appen,
     * eftersom titeln (se {@code titleFrom}) ändå avkortas från det första
     * meddelandet.
     */
    public ChatResult startConversation(UserId owner, String firstMessage) {
        Conversation conversation;
        synchronized (limitGuard) {
            if (conversationRepository.countByOwner(owner) >= maxConversationsPerUser) {
                return new ChatResult.LimitReached(
                        "Du har redan %d konversationer, som är max. Radera en gammal konversation för att starta en ny."
                                .formatted(maxConversationsPerUser));
            }
            conversation = conversationRepository.save(
                    new Conversation(null, owner, titleFrom(firstMessage), Instant.now()));
            conversationRepository.addMessage(
                    new ChatMessage(null, conversation.id(), Role.USER, firstMessage, Instant.now()));
        }
        return generateReply(conversation);
    }

    /**
     * Anropande kod (webblagret, eller en stegklass i tester) ansvarar för
     * att redan ha slagit upp {@code conversation} - men {@code owner}
     * måste ändå matcha dess faktiska ägare, annars kastas ett fel istället
     * för att blint lita på ett objekt som råkat följa med från fel
     * sammanhang (samma "verifiera, lita inte blint"-princip som
     * {@code WineService.removeWine} tillämpar via sitt eget
     * {@code findByIdAndOwner}-krav, fast utan en extra databasläsning här
     * eftersom anroparen redan har det uppslagna objektet i handen).
     */
    public ChatResult postMessage(UserId owner, Conversation conversation, String text) {
        if (!conversation.owner().equals(owner)) {
            throw new IllegalArgumentException("Konversationen tillhör inte den angivna ägaren");
        }
        synchronized (limitGuard) {
            // "+ 2": en lyckad utväxling lägger alltid till både användarens
            // och assistentens meddelande (se generateReply). Gränsen
            // kontrolleras mot vad SLUTRESULTATET skulle bli, inte bara
            // det redan sparade antalet - annars kan en udda konfigurerad
            // gräns överskridas med ett meddelande (antalet är annars alltid
            // jämnt efter en lyckad utväxling, vilket döljer felet vid en
            // jämn gräns som standardvärdet 40).
            if (conversationRepository.countMessages(conversation.id()) + 2 > maxMessagesPerConversation) {
                return new ChatResult.LimitReached(
                        "Den här konversationen har redan %d meddelanden, som är max. Starta en ny konversation för att fortsätta."
                                .formatted(maxMessagesPerConversation));
            }
            conversationRepository.addMessage(new ChatMessage(null, conversation.id(), Role.USER, text, Instant.now()));
        }
        return generateReply(conversation);
    }

    public List<Conversation> listConversations(UserId owner) {
        return conversationRepository.findAllByOwner(owner);
    }

    public Optional<Conversation> findConversation(UserId owner, ConversationId id) {
        return conversationRepository.findByIdAndOwner(id, owner);
    }

    public List<ChatMessage> messages(ConversationId id) {
        return conversationRepository.findMessages(id);
    }

    /** No-op (inte ett fel) om konversationen inte finns/inte ägs av owner. */
    public void deleteConversation(UserId owner, ConversationId id) {
        conversationRepository.deleteByIdAndOwner(id, owner);
    }

    /**
     * Körs UTANFÖR {@code limitGuard} - anropar den externa tjänsten, vilket
     * blockerar den här tråden men inte andra användares chattar.
     * Användarens eget meddelande är redan sparat av anroparen när den här
     * metoden körs.
     */
    private ChatResult generateReply(Conversation conversation) {
        List<Wine> wines = wineRepository.findAllByOwner(conversation.owner());
        List<ChatMessage> history = conversationRepository.findMessages(conversation.id());
        Optional<String> reply = assistant.reply(wines, history);
        if (reply.isEmpty()) {
            return new ChatResult.AssistantUnavailable(conversation);
        }
        ChatMessage assistantMessage = conversationRepository.addMessage(
                new ChatMessage(null, conversation.id(), Role.ASSISTANT, reply.get(), Instant.now()));
        return new ChatResult.Success(conversation, assistantMessage);
    }

    private static String titleFrom(String firstMessage) {
        String trimmed = firstMessage.trim();
        int maxLength = 60;
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "…";
    }
}
