package com.example.winecellar.infrastructure;

import com.example.winecellar.application.ConversationRepository;
import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.ChatMessage.ChatMessageId;
import com.example.winecellar.domain.Conversation;
import com.example.winecellar.domain.Conversation.ConversationId;
import com.example.winecellar.domain.User.UserId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaConversationRepository implements ConversationRepository {

    private final ConversationJpaRepository conversationJpaRepository;
    private final ChatMessageJpaRepository chatMessageJpaRepository;

    public JpaConversationRepository(
            ConversationJpaRepository conversationJpaRepository, ChatMessageJpaRepository chatMessageJpaRepository) {
        this.conversationJpaRepository = conversationJpaRepository;
        this.chatMessageJpaRepository = chatMessageJpaRepository;
    }

    @Override
    public Conversation save(Conversation conversation) {
        ConversationEntity entity = new ConversationEntity();
        entity.setId(conversation.id() != null ? conversation.id().value() : null);
        entity.setOwner(toOwnerReference(conversation.owner()));
        entity.setTitle(conversation.title());
        entity.setCreatedAt(conversation.createdAt());
        return toDomain(conversationJpaRepository.save(entity));
    }

    @Override
    public Optional<Conversation> findByIdAndOwner(ConversationId id, UserId owner) {
        return conversationJpaRepository.findByIdAndOwnerId(id.value(), owner.value())
                .map(JpaConversationRepository::toDomain);
    }

    @Override
    public List<Conversation> findAllByOwner(UserId owner) {
        return conversationJpaRepository.findByOwnerIdOrderByCreatedAtDesc(owner.value()).stream()
                .map(JpaConversationRepository::toDomain)
                .toList();
    }

    /**
     * Raderar meddelandena EXPLICIT före konversationen, i samma transaktion -
     * ingen `ON DELETE CASCADE` i schemat (Hibernate skapar bara FK-constraintet
     * från `@ManyToOne`, ingen cascade-regel), så ett meddelande hade annars
     * blivit föräldralöst.
     */
    @Override
    @Transactional
    public void deleteByIdAndOwner(ConversationId id, UserId owner) {
        if (conversationJpaRepository.findByIdAndOwnerId(id.value(), owner.value()).isPresent()) {
            chatMessageJpaRepository.deleteByConversationId(id.value());
            conversationJpaRepository.deleteById(id.value());
        }
    }

    @Override
    public int countByOwner(UserId owner) {
        return Math.toIntExact(conversationJpaRepository.countByOwnerId(owner.value()));
    }

    @Override
    public ChatMessage addMessage(ChatMessage message) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setConversation(toConversationReference(message.conversationId()));
        entity.setRole(message.role());
        entity.setContent(message.content());
        entity.setCreatedAt(message.createdAt());
        return toDomain(chatMessageJpaRepository.save(entity));
    }

    @Override
    public List<ChatMessage> findMessages(ConversationId conversationId) {
        return chatMessageJpaRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId.value()).stream()
                .map(JpaConversationRepository::toDomain)
                .toList();
    }

    @Override
    public int countMessages(ConversationId conversationId) {
        return Math.toIntExact(chatMessageJpaRepository.countByConversationId(conversationId.value()));
    }

    /** Används av acceptanstesterna för att nollställa tillstånd mellan scenarier. */
    public void deleteAll() {
        chatMessageJpaRepository.deleteAll();
        conversationJpaRepository.deleteAll();
    }

    /**
     * Bygger bara en "skal"-referens med id satt, samma mönster som
     * {@code JpaWineRepository.toOwnerReference} - ingen DB-läsning behövs
     * för att sätta en främmande nyckel vid save.
     */
    private static UserEntity toOwnerReference(UserId owner) {
        UserEntity reference = new UserEntity();
        reference.setId(owner.value());
        return reference;
    }

    private static ConversationEntity toConversationReference(ConversationId conversationId) {
        ConversationEntity reference = new ConversationEntity();
        reference.setId(conversationId.value());
        return reference;
    }

    private static Conversation toDomain(ConversationEntity entity) {
        return new Conversation(
                new ConversationId(entity.getId()),
                new UserId(entity.getOwner().getId()),
                entity.getTitle(),
                entity.getCreatedAt());
    }

    private static ChatMessage toDomain(ChatMessageEntity entity) {
        return new ChatMessage(
                new ChatMessageId(entity.getId()),
                new ConversationId(entity.getConversation().getId()),
                entity.getRole(),
                entity.getContent(),
                entity.getCreatedAt());
    }
}
