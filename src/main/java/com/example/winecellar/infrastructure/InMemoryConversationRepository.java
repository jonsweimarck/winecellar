package com.example.winecellar.infrastructure;

import com.example.winecellar.application.ConversationRepository;
import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.ChatMessage.ChatMessageId;
import com.example.winecellar.domain.Conversation;
import com.example.winecellar.domain.Conversation.ConversationId;
import com.example.winecellar.domain.User.UserId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Används av acceptanstesterna (WINE-48), samma roll som
 * {@link InMemoryWineRepository} - inte Spring-hanterad. Produktions-
 * konfigurationen använder {@link JpaConversationRepository} mot Postgres.
 */
public class InMemoryConversationRepository implements ConversationRepository {

    private final Map<Long, Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<Long, ChatMessage> messages = new ConcurrentHashMap<>();
    private final AtomicLong nextConversationId = new AtomicLong(1);
    private final AtomicLong nextMessageId = new AtomicLong(1);

    @Override
    public Conversation save(Conversation conversation) {
        Conversation toStore = conversation.id() != null
                ? conversation
                : new Conversation(new ConversationId(nextConversationId.getAndIncrement()),
                        conversation.owner(), conversation.title(), conversation.createdAt());
        conversations.put(toStore.id().value(), toStore);
        return toStore;
    }

    /** null owner = oscopeat (matcha oavsett ägare) - se WineRepository/ConversationRepository. */
    @Override
    public Optional<Conversation> findByIdAndOwner(ConversationId id, UserId owner) {
        return Optional.ofNullable(conversations.get(id.value()))
                .filter(conversation -> ownedBy(conversation, owner));
    }

    @Override
    public List<Conversation> findAllByOwner(UserId owner) {
        return conversations.values().stream()
                .filter(conversation -> ownedBy(conversation, owner))
                .sorted(Comparator.comparing(Conversation::createdAt).reversed())
                .toList();
    }

    private static boolean ownedBy(Conversation conversation, UserId owner) {
        return owner == null || owner.equals(conversation.owner());
    }

    @Override
    public void deleteByIdAndOwner(ConversationId id, UserId owner) {
        findByIdAndOwner(id, owner).ifPresent(conversation -> {
            conversations.remove(id.value());
            messages.values().removeIf(message -> message.conversationId().equals(id));
        });
    }

    @Override
    public int countByOwner(UserId owner) {
        return findAllByOwner(owner).size();
    }

    @Override
    public ChatMessage addMessage(ChatMessage message) {
        ChatMessage toStore = new ChatMessage(new ChatMessageId(nextMessageId.getAndIncrement()),
                message.conversationId(), message.role(), message.content(), message.createdAt());
        messages.put(toStore.id().value(), toStore);
        return toStore;
    }

    @Override
    public List<ChatMessage> findMessages(ConversationId conversationId) {
        return messages.values().stream()
                .filter(message -> message.conversationId().equals(conversationId))
                .sorted(Comparator.comparing(ChatMessage::createdAt).thenComparing(m -> m.id().value()))
                .toList();
    }

    @Override
    public int countMessages(ConversationId conversationId) {
        return findMessages(conversationId).size();
    }
}
