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
     * Sätts in som assistentens svar (inte ett kastat fel) om
     * {@link WineChatAssistant} misslyckas helt - användarens eget
     * meddelande är redan sparat vid det laget, och ett vanligt
     * chattmeddelande är enklare att visa i gränssnittet än ett särskilt
     * felläge. Paketprivat, inte private, så testerna kan verifiera texten
     * utan att duplicera den.
     */
    static final String ASSISTANT_UNAVAILABLE_MESSAGE = "Kunde inte få ett svar just nu - försök gärna igen.";

    private final ConversationRepository conversationRepository;
    private final WineRepository wineRepository;
    private final WineChatAssistant assistant;
    private final int maxConversationsPerUser;
    private final int maxMessagesPerConversation;

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
        if (conversationRepository.countByOwner(owner) >= maxConversationsPerUser) {
            return new ChatResult.LimitReached(
                    "Du har redan %d konversationer, som är max. Radera en gammal konversation för att starta en ny."
                            .formatted(maxConversationsPerUser));
        }
        Conversation conversation = conversationRepository.save(
                new Conversation(null, owner, titleFrom(firstMessage), Instant.now()));
        return sendMessage(conversation, firstMessage);
    }

    /**
     * Anropande kod (webblagret, eller en stegklass i tester) ansvarar för
     * att redan ha slagit upp och ägarskapskontrollerat {@code conversation}
     * - samma "repositoryt/tjänsten är dum, anroparen har redan gjort
     * uppslaget"-princip som gäller för {@code WineService.removeWine} och
     * dess {@code findByIdAndOwner}-krav.
     */
    public ChatResult postMessage(Conversation conversation, String text) {
        if (conversationRepository.countMessages(conversation.id()) >= maxMessagesPerConversation) {
            return new ChatResult.LimitReached(
                    "Den här konversationen har redan %d meddelanden, som är max. Starta en ny konversation för att fortsätta."
                            .formatted(maxMessagesPerConversation));
        }
        return sendMessage(conversation, text);
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

    private ChatResult sendMessage(Conversation conversation, String text) {
        conversationRepository.addMessage(new ChatMessage(null, conversation.id(), Role.USER, text, Instant.now()));

        List<Wine> wines = wineRepository.findAllByOwner(conversation.owner());
        List<ChatMessage> history = conversationRepository.findMessages(conversation.id());
        String replyText = assistant.reply(wines, history).orElse(ASSISTANT_UNAVAILABLE_MESSAGE);

        ChatMessage assistantMessage = conversationRepository.addMessage(
                new ChatMessage(null, conversation.id(), Role.ASSISTANT, replyText, Instant.now()));
        return new ChatResult.Success(conversation, assistantMessage);
    }

    private static String titleFrom(String firstMessage) {
        String trimmed = firstMessage.trim();
        int maxLength = 60;
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "…";
    }
}
