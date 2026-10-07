package com.example.winecellar.application;

import com.example.winecellar.domain.ChatMessage;
import com.example.winecellar.domain.Conversation;
import com.example.winecellar.domain.Conversation.ConversationId;
import com.example.winecellar.domain.User.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Port för konversationer och deras meddelanden (WINE-48) - en aggregat,
 * inte två separata portar, eftersom ett meddelande alltid hör till exakt
 * en konversation och aldrig ändras/hämtas fristående (samma princip som
 * `Wine`s bild ligger i samma rad snarare än en egen tabell, se
 * ADR 0004, fast för en förälder/barn-relation istället för ett fält).
 * Ägarskap scopas på samma sätt som {@link WineRepository}.
 */
public interface ConversationRepository {

    Conversation save(Conversation conversation);

    Optional<Conversation> findByIdAndOwner(ConversationId id, UserId owner);

    /** Nyast först - listordningen på konversationsöversikten. */
    List<Conversation> findAllByOwner(UserId owner);

    /** No-op om konversationen inte finns/inte ägs av {@code owner}. */
    void deleteByIdAndOwner(ConversationId id, UserId owner);

    /**
     * WINE-61 (radering av en användare): tar bort ALLA ägarens konversationer
     * med meddelanden. {@code owner} får INTE vara null här (till skillnad från
     * de läsande metoderna betyder null inte "oscopeat" - det hade raderat allas).
     */
    void deleteAllByOwner(UserId owner);

    int countByOwner(UserId owner);

    ChatMessage addMessage(ChatMessage message);

    /** Äldst först - den ordning en konversation faktiskt läses/skickas till assistenten i. */
    List<ChatMessage> findMessages(ConversationId conversationId);

    int countMessages(ConversationId conversationId);
}
